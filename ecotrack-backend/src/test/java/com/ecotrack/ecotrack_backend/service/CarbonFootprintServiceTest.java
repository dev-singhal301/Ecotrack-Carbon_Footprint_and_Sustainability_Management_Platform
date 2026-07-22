package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.CategoryImpactResponse;
import com.ecotrack.ecotrack_backend.dto.FootprintOverviewResponse;
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
    // getTotalCarbonFootprint()
    // ---------------------------------------------------------------

    @Test
    void getTotalCarbonFootprint_returnsSumFromRepository() {
        when(carbonEntryRepository.sumTotalCarbonEmissionByUserId(USER_ID))
                .thenReturn(new BigDecimal("150.50"));

        BigDecimal total = service.getTotalCarbonFootprint(USER_ID);

        assertThat(total).isEqualByComparingTo("150.50");
    }

    @Test
    void getTotalCarbonFootprint_returnsZeroWhenNull() {
        when(carbonEntryRepository.sumTotalCarbonEmissionByUserId(USER_ID))
                .thenReturn(null);

        BigDecimal total = service.getTotalCarbonFootprint(USER_ID);

        assertThat(total).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------
    // getCarbonSavedVsBaseline() & getSavingsPercentage()
    // ---------------------------------------------------------------

    @Test
    void getCarbonSavedVsBaseline_returnsPositiveSavingsWhenEmissionsDecreased() {
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(entry(CarbonCategory.ELECTRICITY, new BigDecimal("80.00"), LocalDate.now())))
                .thenReturn(List.of(entry(CarbonCategory.ELECTRICITY, new BigDecimal("100.00"), LocalDate.now().minusDays(35))));

        BigDecimal saved = service.getCarbonSavedVsBaseline(USER_ID);

        assertThat(saved).isEqualByComparingTo("20.00");
    }

    @Test
    void getSavingsPercentage_calculatesCorrectPercentageDrop() {
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(entry(CarbonCategory.ELECTRICITY, new BigDecimal("80.00"), LocalDate.now())))
                .thenReturn(List.of(entry(CarbonCategory.ELECTRICITY, new BigDecimal("100.00"), LocalDate.now().minusDays(35))));

        Double percentage = service.getSavingsPercentage(USER_ID);

        assertThat(percentage).isEqualTo(20.0);
    }

    // ---------------------------------------------------------------
    // getCategoryImpactList()
    // ---------------------------------------------------------------

    @Test
    void getCategoryImpactList_calculatesPercentageSharesCorrectly() {
        Object[] row1 = new Object[]{CarbonCategory.ELECTRICITY, new BigDecimal("60.00")};
        Object[] row2 = new Object[]{CarbonCategory.TRANSPORTATION, new BigDecimal("40.00")};

        when(carbonEntryRepository.findCategoryImpactByUserId(USER_ID))
                .thenReturn(List.of(row1, row2));
        when(carbonEntryRepository.sumTotalCarbonEmissionByUserId(USER_ID))
                .thenReturn(new BigDecimal("100.00"));

        List<CategoryImpactResponse> impacts = service.getCategoryImpactList(USER_ID);

        assertThat(impacts).hasSize(2);

        CategoryImpactResponse expectedFirst = new CategoryImpactResponse("ELECTRICITY", new BigDecimal("60.00"), 60.0);
        CategoryImpactResponse expectedSecond = new CategoryImpactResponse("TRANSPORTATION", new BigDecimal("40.00"), 40.0);

        assertThat(impacts.get(0)).usingRecursiveComparison().isEqualTo(expectedFirst);
        assertThat(impacts.get(1)).usingRecursiveComparison().isEqualTo(expectedSecond);
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

        verifyNoInteractions(userRepository);

        ArgumentCaptor<CarbonFootprint> captor = ArgumentCaptor.forClass(CarbonFootprint.class);
        verify(carbonFootprintRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getFootprintId()).isEqualTo(99L);
        assertThat(captor.getValue().getFoodEmission()).isEqualByComparingTo("7.00");
    }
}