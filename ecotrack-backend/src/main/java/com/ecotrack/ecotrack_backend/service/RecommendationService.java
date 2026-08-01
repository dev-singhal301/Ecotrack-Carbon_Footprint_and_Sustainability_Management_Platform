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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final CarbonEntryRepository carbonEntryRepository;
    private final RecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final RecommendationRuleEngine ruleEngine = new RecommendationRuleEngine();

    public RecommendationService(CarbonEntryRepository carbonEntryRepository,
                                 RecommendationRepository recommendationRepository,
                                 UserRepository userRepository) {
        this.carbonEntryRepository = carbonEntryRepository;
        this.recommendationRepository = recommendationRepository;
        this.userRepository = userRepository;
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

        List<TopRecommendationDto> topDtos = existing.stream()
                .map(this::toTopDto)
                .toList();

        List<CategoryBreakdownDto> breakdown = computeLiveBreakdown(userId);

        return new RecommendationResponse(topDtos, breakdown, null);
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

        List<CarbonEntry> entries = carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, from, to);

        // Decision A: clear old rows first, regardless of outcome below.
        recommendationRepository.deleteByUserId(userId);

        if (entries.isEmpty()) {
            return new RecommendationResponse(List.of(), buildZeroBreakdown(), ZERO_EMISSION_MESSAGE);
        }

        Map<CarbonCategory, CategoryAggregate> aggregates = aggregateByCategory(entries);

        BigDecimal totalEmission = aggregates.values().stream()
                .map(CategoryAggregate::sumEmission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Decision #2: only categories with actual (nonzero) emissions in the
        // window are eligible for the top 3 — never pad with 0% categories.
        List<CarbonCategory> topCategories = aggregates.values().stream()
                .filter(agg -> agg.sumEmission().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(CategoryAggregate::sumEmission).reversed())
                .limit(3)
                .map(CategoryAggregate::category)
                .toList();

        User userRef = userRepository.getReferenceById(userId);

        List<Recommendation> toSave = new ArrayList<>();
        for (CarbonCategory category : topCategories) {
            CategoryAggregate agg = aggregates.get(category);

            // Rule engine thresholds on summed `amount` (unit-specific: km, kWh, kg, litres).
            String suggestionText = ruleEngine.getSuggestion(category, agg.sumAmount());

            Recommendation rec = new Recommendation();
            rec.setUser(userRef);
            rec.setCategory(category);
            rec.setSuggestionText(suggestionText);
            // impactEstimate = this category's carbonEmission contribution (kg CO2) in the window.
            rec.setImpactEstimate(agg.sumEmission());
            toSave.add(rec);
        }

        List<Recommendation> saved = recommendationRepository.saveAll(toSave);

        List<TopRecommendationDto> topDtos = saved.stream()
                .sorted(Comparator.comparing(Recommendation::getImpactEstimate).reversed())
                .map(this::toTopDto)
                .toList();

        List<CategoryBreakdownDto> breakdown = buildBreakdown(aggregates, totalEmission);

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

        for (CarbonCategory category : CarbonCategory.values()) {
            amountByCategory.put(category, BigDecimal.ZERO);
            emissionByCategory.put(category, BigDecimal.ZERO);
        }

        for (CarbonEntry entry : entries) {
            CarbonCategory category = entry.getCategory();
            amountByCategory.merge(category, entry.getAmount(), BigDecimal::add);
            emissionByCategory.merge(category, entry.getCarbonEmission(), BigDecimal::add);
        }

        Map<CarbonCategory, CategoryAggregate> result = new EnumMap<>(CarbonCategory.class);
        for (CarbonCategory category : CarbonCategory.values()) {
            result.put(category, new CategoryAggregate(
                    category,
                    amountByCategory.get(category),
                    emissionByCategory.get(category)
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

    private TopRecommendationDto toTopDto(Recommendation rec) {
        return new TopRecommendationDto(
                rec.getCategory().name(),
                rec.getSuggestionText(),
                rec.getImpactEstimate()
        );
    }

    /** Internal aggregate holder — not persisted, just a per-category calculation result. */
    private record CategoryAggregate(CarbonCategory category, BigDecimal sumAmount, BigDecimal sumEmission) {}
}