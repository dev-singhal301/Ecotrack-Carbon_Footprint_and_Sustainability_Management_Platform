package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.FootprintOverviewResponse;
import com.ecotrack.ecotrack_backend.dto.FootprintTrendResponse;
import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.CarbonEntry;
import com.ecotrack.ecotrack_backend.entity.CarbonFootprint;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.CarbonEntryRepository;
import com.ecotrack.ecotrack_backend.repository.CarbonFootprintRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarbonFootprintServiceTest {

    @Mock private CarbonEntryRepository carbonEntryRepository;
    @Mock private CarbonFootprintRepository carbonFootprintRepository;
    @Mock private UserRepository userRepository;

    private CarbonFootprintService service;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new CarbonFootprintService(carbonEntryRepository, carbonFootprintRepository, userRepository);
    }

    private CarbonEntry entry(CarbonCategory category, BigDecimal emission, LocalDate date) {
        CarbonEntry e = new CarbonEntry();
        e.setCategory(category);
        e.setCarbonEmission(emission);
        e.setActivityDate(date);
        return e;
    }

    // ---------------------------------------------------------------
    // getOverview()
    // ---------------------------------------------------------------

    @Test
    void getOverview_sumsTotalAndBreaksDownByCategory() {
        LocalDate today = LocalDate.now();
        List<CarbonEntry> entries = List.of(
                entry(CarbonCategory.ELECTRICITY, new BigDecimal("10.00"), today),
                entry(CarbonCategory.ELECTRICITY, new BigDecimal("5.00"), today.minusDays(2)),
                entry(CarbonCategory.TRANSPORTATION, new BigDecimal("3.50"), today.minusDays(1))
        );

        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(entries);

        FootprintOverviewResponse result = service.getOverview(USER_ID);

        assertThat(result.getTotalEmission()).isEqualByComparingTo("18.50");
        assertThat(result.getCategoryBreakdown().get(CarbonCategory.ELECTRICITY)).isEqualByComparingTo("15.00");
        assertThat(result.getCategoryBreakdown().get(CarbonCategory.TRANSPORTATION)).isEqualByComparingTo("3.50");
        // Categories with no entries should still be present, at zero — not missing from the map.
        assertThat(result.getCategoryBreakdown().get(CarbonCategory.FOOD)).isEqualByComparingTo("0");
        assertThat(result.getCategoryBreakdown().get(CarbonCategory.WASTE)).isEqualByComparingTo("0");
        assertThat(result.getCategoryBreakdown().get(CarbonCategory.WATER)).isEqualByComparingTo("0");
        assertThat(result.getCategoryBreakdown().get(CarbonCategory.FUEL)).isEqualByComparingTo("0");
    }

    @Test
    void getOverview_returnsZeroTotalWhenNoEntries() {
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of());

        FootprintOverviewResponse result = service.getOverview(USER_ID);

        assertThat(result.getTotalEmission()).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------
    // getTrend()
    // ---------------------------------------------------------------

    @Test
    void getTrend_noPreviousData_returnsNullPercentChange() {
        // First call inside getTrend() is for the current window, second is for the previous window.
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(entry(CarbonCategory.ELECTRICITY, new BigDecimal("10.00"), LocalDate.now())))
                .thenReturn(List.of()); // previous period empty

        FootprintTrendResponse trend = service.getTrend(USER_ID);

        assertThat(trend.isHasPreviousData()).isFalse();
        assertThat(trend.getPercentChange()).isNull();
        assertThat(trend.getCurrentTotal()).isEqualByComparingTo("10.00");
    }

    @Test
    void getTrend_emissionsIncreased_returnsPositivePercentChange() {
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(entry(CarbonCategory.ELECTRICITY, new BigDecimal("20.00"), LocalDate.now())))
                .thenReturn(List.of(entry(CarbonCategory.ELECTRICITY, new BigDecimal("10.00"), LocalDate.now().minusDays(40))));

        FootprintTrendResponse trend = service.getTrend(USER_ID);

        assertThat(trend.isHasPreviousData()).isTrue();
        // (20 - 10) / 10 * 100 = 100% increase
        assertThat(trend.getPercentChange()).isEqualByComparingTo("100.0000");
    }

    @Test
    void getTrend_emissionsDecreased_returnsNegativePercentChange() {
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(entry(CarbonCategory.ELECTRICITY, new BigDecimal("5.00"), LocalDate.now())))
                .thenReturn(List.of(entry(CarbonCategory.ELECTRICITY, new BigDecimal("10.00"), LocalDate.now().minusDays(40))));

        FootprintTrendResponse trend = service.getTrend(USER_ID);

        // (5 - 10) / 10 * 100 = -50% — a decrease should read as negative (green, per the frontend convention)
        assertThat(trend.getPercentChange()).isEqualByComparingTo("-50.0000");
    }

    // ---------------------------------------------------------------
    // logDailySnapshot() — the upsert behavior
    // ---------------------------------------------------------------

    @Test
    void logDailySnapshot_createsNewRowWhenNoneExistsForToday() {
        User user = new User();
        user.setId(USER_ID);

        when(carbonFootprintRepository.findByUserIdAndPeriod(eq(USER_ID), anyString()))
                .thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(entry(CarbonCategory.WATER, new BigDecimal("2.00"), LocalDate.now())));

        service.logDailySnapshot(USER_ID);

        ArgumentCaptor<CarbonFootprint> captor = ArgumentCaptor.forClass(CarbonFootprint.class);
        verify(carbonFootprintRepository, times(1)).save(captor.capture());

        CarbonFootprint saved = captor.getValue();
        assertThat(saved.getPeriod()).isEqualTo(LocalDate.now().toString());
        assertThat(saved.getWaterEmission()).isEqualByComparingTo("2.00");
        assertThat(saved.getTotalEmission()).isEqualByComparingTo("2.00");
    }

    @Test
    void logDailySnapshot_updatesExistingRowInsteadOfCreatingDuplicate() {
        CarbonFootprint existing = new CarbonFootprint();
        existing.setFootprintId(99L);
        existing.setPeriod(LocalDate.now().toString());

        when(carbonFootprintRepository.findByUserIdAndPeriod(eq(USER_ID), anyString()))
                .thenReturn(Optional.of(existing));
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(entry(CarbonCategory.FOOD, new BigDecimal("7.00"), LocalDate.now())));

        service.logDailySnapshot(USER_ID);

        // userRepository should NOT be consulted when updating an existing row —
        // only needed to build a brand-new one.
        verifyNoInteractions(userRepository);

        ArgumentCaptor<CarbonFootprint> captor = ArgumentCaptor.forClass(CarbonFootprint.class);
        verify(carbonFootprintRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getFootprintId()).isEqualTo(99L); // same row, not a new one
        assertThat(captor.getValue().getFoodEmission()).isEqualByComparingTo("7.00");
    }
}