package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.CategoryImpactResponse;
import com.ecotrack.ecotrack_backend.dto.FootprintOverviewResponse;
import com.ecotrack.ecotrack_backend.dto.FootprintTrendPointResponse;
import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.CarbonEntry;
import com.ecotrack.ecotrack_backend.entity.CarbonFootprint;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.CarbonEntryRepository;
import com.ecotrack.ecotrack_backend.repository.CarbonFootprintRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CarbonFootprintService {

    private static final int ROLLING_WINDOW_DAYS = 30;

    private final CarbonEntryRepository carbonEntryRepository;
    private final CarbonFootprintRepository carbonFootprintRepository;
    private final UserRepository userRepository;

    public CarbonFootprintService(CarbonEntryRepository carbonEntryRepository,
                                  CarbonFootprintRepository carbonFootprintRepository,
                                  UserRepository userRepository) {
        this.carbonEntryRepository = carbonEntryRepository;
        this.carbonFootprintRepository = carbonFootprintRepository;
        this.userRepository = userRepository;
    }

    // ── Dashboard Blueprint Methods ──

    /**
     * Section 2: Total Carbon Footprint across all logged activities.
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalCarbonFootprint(Long userId) {
        BigDecimal total = carbonEntryRepository.sumTotalCarbonEmissionByUserId(userId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Section 2: Carbon Saved vs Baseline (comparing current 30-day rolling window to previous 30-day window).
     * Returns savings as a positive number if emissions went down.
     */
    @Transactional(readOnly = true)
    public BigDecimal getCarbonSavedVsBaseline(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate currentStart = today.minusDays(ROLLING_WINDOW_DAYS - 1);
        LocalDate previousEnd = currentStart.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(ROLLING_WINDOW_DAYS - 1);

        BigDecimal currentTotal = sumTotal(userId, currentStart, today);
        BigDecimal previousTotal = sumTotal(userId, previousStart, previousEnd);

        if (previousTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal savings = previousTotal.subtract(currentTotal);
        return savings.compareTo(BigDecimal.ZERO) > 0 ? savings : BigDecimal.ZERO;
    }

    /**
     * Section 2: Percentage reduction badge (e.g., 12.5% drop).
     */
    @Transactional(readOnly = true)
    public Double getSavingsPercentage(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate currentStart = today.minusDays(ROLLING_WINDOW_DAYS - 1);
        LocalDate previousEnd = currentStart.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(ROLLING_WINDOW_DAYS - 1);

        BigDecimal currentTotal = sumTotal(userId, currentStart, today);
        BigDecimal previousTotal = sumTotal(userId, previousStart, previousEnd);

        if (previousTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }

        BigDecimal diff = previousTotal.subtract(currentTotal);
        BigDecimal percentage = diff.divide(previousTotal, 4, RoundingMode.HALF_UP)
                                     .multiply(BigDecimal.valueOf(100));

        return percentage.doubleValue();
    }

    /**
     * Section 3: Ranked Category Impact List.
     * Uses repository aggregate query to fetch emissions per category, ordered descending.
     */
    @Transactional(readOnly = true)
    public List<CategoryImpactResponse> getCategoryImpactList(Long userId) {
        List<Object[]> rawData = carbonEntryRepository.findCategoryImpactByUserId(userId);
        BigDecimal grandTotal = getTotalCarbonFootprint(userId);

        List<CategoryImpactResponse> responseList = new ArrayList<>();

        for (Object[] row : rawData) {
            CarbonCategory category = (CarbonCategory) row[0];
            BigDecimal emissions = (BigDecimal) row[1];

            double percentageShare = 0.0;
            if (grandTotal.compareTo(BigDecimal.ZERO) > 0 && emissions != null) {
                percentageShare = emissions.divide(grandTotal, 4, RoundingMode.HALF_UP)
                                           .multiply(BigDecimal.valueOf(100))
                                           .doubleValue();
            }

            responseList.add(new CategoryImpactResponse(
                category.name(),
                emissions != null ? emissions : BigDecimal.ZERO,
                percentageShare
            ));
        }

        return responseList;
    }

    // ── Live reads for the dashboard ──

    @Transactional(readOnly = true)
    public FootprintOverviewResponse getOverview(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(ROLLING_WINDOW_DAYS - 1);
        Map<CarbonCategory, BigDecimal> breakdown = sumByCategory(userId, windowStart, today);
        BigDecimal total = breakdown.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FootprintOverviewResponse(total, breakdown);
    }

    /**
     * Section: Emissions Trend Chart — daily snapshots from the last `days` days, oldest first.
     */
    @Transactional(readOnly = true)
    public List<FootprintTrendPointResponse> getFootprintTrend(Long userId, int days) {
        int clampedDays = Math.max(1, Math.min(days, 90));
        LocalDate windowStart = LocalDate.now().minusDays(clampedDays - 1L);
        String periodFloor = windowStart.toString();

        List<CarbonFootprint> rows = carbonFootprintRepository
                .findByUserIdAndPeriodGreaterThanEqualOrderByPeriodAsc(userId, periodFloor);

        List<FootprintTrendPointResponse> points = new ArrayList<>();
        for (CarbonFootprint row : rows) {
            points.add(new FootprintTrendPointResponse(
                    row.getPeriod(),
                    row.getTotalEmission(),
                    row.getElectricityEmission(),
                    row.getTransportationEmission(),
                    row.getFuelEmission(),
                    row.getWaterEmission(),
                    row.getWasteEmission(),
                    row.getFoodEmission()
            ));
        }
        return points;
    }

    /**
     * Section: Emissions Trend Chart — custom start/end date range, oldest first.
     */
    @Transactional(readOnly = true)
    public List<FootprintTrendPointResponse> getFootprintTrendByRange(Long userId, LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "Start date must not be null");
        Objects.requireNonNull(to, "End date must not be null");

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Start date must be on or before end date");
        }
        if (ChronoUnit.DAYS.between(from, to) > 365) {
            throw new IllegalArgumentException("Date range cannot exceed 366 days");
        }

        List<CarbonFootprint> rows = carbonFootprintRepository
                .findByUserIdAndPeriodBetweenOrderByPeriodAsc(userId, from.toString(), to.toString());

        List<FootprintTrendPointResponse> points = new ArrayList<>();
        for (CarbonFootprint row : rows) {
            points.add(new FootprintTrendPointResponse(
                    row.getPeriod(),
                    row.getTotalEmission(),
                    row.getElectricityEmission(),
                    row.getTransportationEmission(),
                    row.getFuelEmission(),
                    row.getWaterEmission(),
                    row.getWasteEmission(),
                    row.getFoodEmission()
            ));
        }
        return points;
    }

    // ── Historical log — one row per user per calendar day, upserted ──
    @Transactional
    public void logDailySnapshot(Long userId) {
        LocalDate today = LocalDate.now();
        String periodKey = today.toString();
        LocalDate windowStart = today.minusDays(ROLLING_WINDOW_DAYS - 1);

        Map<CarbonCategory, BigDecimal> breakdown = sumByCategory(userId, windowStart, today);
        BigDecimal total = breakdown.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        CarbonFootprint footprint = carbonFootprintRepository.findByUserIdAndPeriod(userId, periodKey)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
                    CarbonFootprint newRow = new CarbonFootprint();
                    newRow.setUser(user);
                    newRow.setPeriod(periodKey);
                    return newRow;
                });

        footprint.setTotalEmission(total);
        footprint.setElectricityEmission(breakdown.getOrDefault(CarbonCategory.ELECTRICITY, BigDecimal.ZERO));
        footprint.setTransportationEmission(breakdown.getOrDefault(CarbonCategory.TRANSPORTATION, BigDecimal.ZERO));
        footprint.setFuelEmission(breakdown.getOrDefault(CarbonCategory.FUEL, BigDecimal.ZERO));
        footprint.setWaterEmission(breakdown.getOrDefault(CarbonCategory.WATER, BigDecimal.ZERO));
        footprint.setWasteEmission(breakdown.getOrDefault(CarbonCategory.WASTE, BigDecimal.ZERO));
        footprint.setFoodEmission(breakdown.getOrDefault(CarbonCategory.FOOD, BigDecimal.ZERO));

        carbonFootprintRepository.save(footprint);
    }

    // ── Helpers ──
    private BigDecimal sumTotal(Long userId, LocalDate from, LocalDate to) {
        return carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, from, to).stream()
                .map(CarbonEntry::getCarbonEmission)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<CarbonCategory, BigDecimal> sumByCategory(Long userId, LocalDate from, LocalDate to) {
        List<CarbonEntry> entries = carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, from, to);
        Map<CarbonCategory, BigDecimal> breakdown = new EnumMap<>(CarbonCategory.class);
        for (CarbonCategory category : CarbonCategory.values()) {
            breakdown.put(category, BigDecimal.ZERO);
        }
        for (CarbonEntry entry : entries) {
            if (entry.getCarbonEmission() == null) continue;
            breakdown.merge(entry.getCategory(), entry.getCarbonEmission(), BigDecimal::add);
        }
        return breakdown;
    }
}