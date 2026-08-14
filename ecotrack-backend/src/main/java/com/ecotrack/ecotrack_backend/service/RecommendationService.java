package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.CategoryBreakdownDto;
import com.ecotrack.ecotrack_backend.dto.RecommendationResponse;
import com.ecotrack.ecotrack_backend.dto.TopRecommendationDto;
import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.CarbonEntry;
import com.ecotrack.ecotrack_backend.entity.Recommendation;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.CarbonEntryRepository;
import com.ecotrack.ecotrack_backend.repository.RecommendationRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.dto.AiRecommendationDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationService {

    private static final int WINDOW_DAYS = 30;
    private static final String ZERO_EMISSION_MESSAGE =
            "No emissions logged yet. Start tracking to get personalized tips!";
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final CarbonEntryRepository carbonEntryRepository;
    private final RecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final RecommendationRuleEngine ruleEngine;
    private final AiTipService aiTipService;

    public RecommendationService(CarbonEntryRepository carbonEntryRepository,
                                 RecommendationRepository recommendationRepository,
                                 UserRepository userRepository,
                                 RecommendationRuleEngine ruleEngine,
                                 AiTipService aiTipService) {
        this.carbonEntryRepository = carbonEntryRepository;
        this.recommendationRepository = recommendationRepository;
        this.userRepository = userRepository;
        this.ruleEngine = ruleEngine;
        this.aiTipService = aiTipService;
    }

    /**
     * Decision B: if no recommendations exist yet for this user, compute and
     * persist them first (same logic /refresh uses), then return.
     */
    @Transactional
    public RecommendationResponse getRecommendations(Long userId) {
        List<Recommendation> existing = recommendationRepository.findByUserIdOrderByImpactEstimateDesc(userId);

        if (existing.isEmpty()) {
            return computeAndPersist(userId);
        }

        List<CategoryBreakdownDto> breakdown = computeLiveBreakdown(userId);
        Map<String, BigDecimal> percentageByCategory = breakdown.stream()
                .collect(java.util.stream.Collectors.toMap(CategoryBreakdownDto::getCategory, CategoryBreakdownDto::getPercentage));

        List<TopRecommendationDto> topDtos = existing.stream()
                .map(rec -> new TopRecommendationDto(
                        rec.getCategory().name(),
                        percentageByCategory.getOrDefault(rec.getCategory().name(), BigDecimal.ZERO),
                        rec.getImpactEstimate(),
                        deserializeDetail(rec.getSuggestionText(), rec.getCategory())))
                .toList();

        return new RecommendationResponse(topDtos, breakdown, null);
    }

    private String serializeDetail(AiRecommendationDetail detail) {
        try {
            return jsonMapper.writeValueAsString(detail);
        } catch (Exception e) {
            // Should never happen with this simple a shape, but never let a
            // serialization hiccup break the whole recommendation write.
            return "{\"title\":\"\",\"why\":\"\",\"action\":\"" + detail.getAction() + "\",\"alternatives\":[],\"impact\":\"\",\"source\":\"STANDARD\"}";
        }
    }

    /**
     * Handles rows written before this change (plain rule-engine text, not JSON)
     * so existing users don't get a 500 on their first GET after this deploys.
     */
    private AiRecommendationDetail deserializeDetail(String stored, CarbonCategory category) {
        try {
            return jsonMapper.readValue(stored, AiRecommendationDetail.class);
        } catch (Exception e) {
            String label = category.name().charAt(0) + category.name().substring(1).toLowerCase();
            return new AiRecommendationDetail(label + " needs attention", "", stored, List.of(), "", "STANDARD");
        }
    }

    /**
     * Decision A: always delete this user's existing rows, then recompute
     * and insert the fresh top 3. Wrapped in one transaction so a failure
     * partway through rolls back the delete too — the user never ends up
     * with zero rows because an insert failed after a successful delete.
     */
    @Transactional
    public RecommendationResponse refreshRecommendations(Long userId) {
        return computeAndPersist(userId);
    }

    /**
     * Core compute-and-persist logic, shared by both public entry points above.
     * Deliberately NOT called as "this.refreshRecommendations(...)" or
     * "this.getRecommendations(...)" from within this class — calling a
     * public @Transactional method from another method on the same bean
     * bypasses Spring's transactional proxy entirely, silently ignoring
     * the annotation. Both public methods call this private method directly
     * instead, so it always runs inside whichever @Transactional context
     * the caller already started.
     */
    private RecommendationResponse computeAndPersist(Long userId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(WINDOW_DAYS);

        List<CarbonEntry> entries =
                carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, from, to);

        if (entries.isEmpty()) {
            recommendationRepository.deleteByUserId(userId);
            return new RecommendationResponse(
                    List.of(),
                    buildZeroBreakdown(),
                    ZERO_EMISSION_MESSAGE
            );
        }

        Map<CarbonCategory, CategoryAggregate> aggregates =
                aggregateByCategory(entries);

        BigDecimal totalEmission = aggregates.values().stream()
                .map(CategoryAggregate::sumEmission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CarbonCategory> topCategories = aggregates.values().stream()
                .filter(agg -> agg.sumEmission().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(CategoryAggregate::sumEmission).reversed())
                .limit(3)
                .map(CategoryAggregate::category)
                .toList();

        List<CategoryBreakdownDto> breakdown = buildBreakdown(aggregates, totalEmission);
        Map<String, BigDecimal> percentageByCategory = breakdown.stream()
                .collect(java.util.stream.Collectors.toMap(CategoryBreakdownDto::getCategory, CategoryBreakdownDto::getPercentage));

        // Build every candidate's rule-based fallback up front — cheap, and this is
        // what gets used unconditionally if the single Gemini batch call fails.
        List<AiTipService.CategoryCandidate> candidates = new ArrayList<>();
        for (CarbonCategory category : topCategories) {
            CategoryAggregate agg = aggregates.get(category);
            String ruleBasedText = ruleEngine.getSuggestion(category, agg.sumAmount());
            AiRecommendationDetail fallbackDetail = ruleEngine.getStructuredSuggestion(
                    category, agg.sumAmount(), percentageByCategory.getOrDefault(category.name(), BigDecimal.ZERO));
            candidates.add(new AiTipService.CategoryCandidate(category, agg.sumAmount(), agg.unit(), ruleBasedText, fallbackDetail));
        }

        // ONE call for all top-3 categories — not one call per category.
        List<AiRecommendationDetail> details = aiTipService.generateBatch(candidates, breakdown, totalEmission);

        User userRef = userRepository.getReferenceById(userId);
        List<Recommendation> toSave = new ArrayList<>();
        for (int i = 0; i < topCategories.size(); i++) {
            CarbonCategory category = topCategories.get(i);
            CategoryAggregate agg = aggregates.get(category);
            AiRecommendationDetail detail = details.get(i);

            Recommendation rec = new Recommendation();
            rec.setUser(userRef);
            rec.setCategory(category);
            rec.setSuggestionText(serializeDetail(detail)); // JSON, not plain text
            rec.setImpactEstimate(agg.sumEmission());
            toSave.add(rec);
        }

        recommendationRepository.deleteByUserId(userId);
        recommendationRepository.saveAll(toSave);

        List<TopRecommendationDto> topDtos = new ArrayList<>();
        for (int i = 0; i < topCategories.size(); i++) {
            CarbonCategory category = topCategories.get(i);
            BigDecimal pct = percentageByCategory.getOrDefault(category.name(), BigDecimal.ZERO);
            topDtos.add(new TopRecommendationDto(category.name(), pct, aggregates.get(category).sumEmission(), details.get(i)));
        }
        topDtos.sort(Comparator.comparing(TopRecommendationDto::getImpactEstimate).reversed());

        return new RecommendationResponse(topDtos, breakdown, null);
    }

    /**
     * Recomputes the full 6-category percentage breakdown live, every call —
     * per the guide (§3 Step 8), this list is never persisted, only the top 3 are.
     */
    private List<CategoryBreakdownDto> computeLiveBreakdown(Long userId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(WINDOW_DAYS);

        List<CarbonEntry> entries = carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, from, to);

        if (entries.isEmpty()) {
            return buildZeroBreakdown();
        }

        Map<CarbonCategory, CategoryAggregate> aggregates = aggregateByCategory(entries);
        BigDecimal totalEmission = aggregates.values().stream()
                .map(CategoryAggregate::sumEmission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return buildBreakdown(aggregates, totalEmission);
    }

    /**
     * Groups window entries by category, tracking TWO separate sums per category:
     * - sumAmount:   raw unit total (km, kWh, kg, litres) — used by the rule engine thresholds.
     * - sumEmission: carbonEmission total (always kg CO2) — used for ranking/percentages,
     *                since raw amounts across categories are not comparable units.
     * All 6 categories are always present in the result, even at zero, so downstream
     * code never needs a null-check per category.
     */
    private Map<CarbonCategory, CategoryAggregate> aggregateByCategory(List<CarbonEntry> entries) {
        Map<CarbonCategory, BigDecimal> amountByCategory = new EnumMap<>(CarbonCategory.class);
        Map<CarbonCategory, BigDecimal> emissionByCategory = new EnumMap<>(CarbonCategory.class);
        Map<CarbonCategory, String> unitByCategory = new EnumMap<>(CarbonCategory.class);

        for (CarbonCategory category : CarbonCategory.values()) {
            amountByCategory.put(category, BigDecimal.ZERO);
            emissionByCategory.put(category, BigDecimal.ZERO);
            unitByCategory.put(category, "");
        }

        for (CarbonEntry entry : entries) {
            CarbonCategory category = entry.getCategory();
            amountByCategory.merge(category, entry.getAmount(), BigDecimal::add);
            emissionByCategory.merge(category, entry.getCarbonEmission(), BigDecimal::add);
            // Unit is consistent per category by convention (km, kWh, kg, litres) —
            // last-write-wins across entries in the window is fine.
            // NOTE: if getUnit() returns an enum rather than String in your entity,
            // change this to entry.getUnit().name() or .toString().
            unitByCategory.put(category, entry.getUnit());
        }

        Map<CarbonCategory, CategoryAggregate> result = new EnumMap<>(CarbonCategory.class);
        for (CarbonCategory category : CarbonCategory.values()) {
            result.put(category, new CategoryAggregate(
                    category,
                    amountByCategory.get(category),
                    emissionByCategory.get(category),
                    unitByCategory.get(category)
            ));
        }
        return result;
    }

    private List<CategoryBreakdownDto> buildBreakdown(Map<CarbonCategory, CategoryAggregate> aggregates,
                                                      BigDecimal totalEmission) {
        List<CategoryBreakdownDto> result = new ArrayList<>();

        for (CategoryAggregate agg : aggregates.values()) {
            BigDecimal percentage;
            if (totalEmission.compareTo(BigDecimal.ZERO) == 0) {
                percentage = BigDecimal.ZERO;
            } else {
                percentage = agg.sumEmission()
                        .divide(totalEmission, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
            }
            result.add(new CategoryBreakdownDto(agg.category().name(), percentage));
        }

        // Stable sort: ties keep CarbonCategory declaration order as a deterministic tie-break.
        result.sort(Comparator.comparing(CategoryBreakdownDto::getPercentage).reversed());
        return result;
    }

    private List<CategoryBreakdownDto> buildZeroBreakdown() {
        List<CategoryBreakdownDto> result = new ArrayList<>();
        for (CarbonCategory category : CarbonCategory.values()) {
            result.add(new CategoryBreakdownDto(category.name(), BigDecimal.ZERO));
        }
        return result;
    }

    /** Internal aggregate holder — not persisted, just a per-category calculation result. */
    private record CategoryAggregate(CarbonCategory category, BigDecimal sumAmount, BigDecimal sumEmission, String unit) {}
}