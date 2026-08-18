package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.CarbonEntryRequest;
import com.ecotrack.ecotrack_backend.dto.CarbonEntryResponse;
import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.CarbonEntry;
import com.ecotrack.ecotrack_backend.entity.EmissionFactor;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.exception.CarbonEntryNotFoundException;
import com.ecotrack.ecotrack_backend.exception.InvalidCategoryException;
import com.ecotrack.ecotrack_backend.repository.CarbonEntryRepository;
import com.ecotrack.ecotrack_backend.repository.EmissionFactorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ecotrack.ecotrack_backend.repository.GoalRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarbonEntryServiceTest {

    @Mock
    private CarbonEntryRepository carbonEntryRepository;

    @Mock
    private EmissionFactorRepository emissionFactorRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private GoalService goalService;

    @Mock
    private LeaderboardService leaderboardService;

    @Mock
    private UserChallengeService userChallengeService;

    @InjectMocks
    private CarbonEntryService carbonEntryService;

    private User testUser;
    private EmissionFactor transportationFactor;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);

        transportationFactor = new EmissionFactor();
        transportationFactor.setEmissionFactorId(1L);
        transportationFactor.setCategory(CarbonCategory.TRANSPORTATION);
        transportationFactor.setUnit("km");
        transportationFactor.setFactorValue(new BigDecimal("0.21"));
    }

    // --- createEntry ---

    @Test
    void createEntry_validRequest_calculatesEmissionCorrectly() {
        CarbonEntryRequest request = new CarbonEntryRequest();
        request.setCategory("TRANSPORTATION");
        request.setAmount(new BigDecimal("10"));
        request.setUnit("km");
        request.setActivityDate(LocalDate.of(2026, 7, 13));

        when(emissionFactorRepository.findByCategory(CarbonCategory.TRANSPORTATION))
                .thenReturn(Optional.of(transportationFactor));

        // Simulate the DB assigning an ID on save
        when(carbonEntryRepository.save(any(CarbonEntry.class))).thenAnswer(invocation -> {
            CarbonEntry saved = invocation.getArgument(0);
            saved.setEntryId(100L);
            return saved;
        });

        CarbonEntryResponse response = carbonEntryService.createEntry(request, testUser);

        assertEquals(100L, response.getEntryId());
        assertEquals("TRANSPORTATION", response.getCategory());
        assertEquals(0, new BigDecimal("2.10").compareTo(response.getCarbonEmission()));
        verify(carbonEntryRepository, times(1)).save(any(CarbonEntry.class));
    }

    @Test
    void createEntry_lowercaseCategory_isNormalizedToEnum() {
        CarbonEntryRequest request = new CarbonEntryRequest();
        request.setCategory("transportation"); // lowercase input
        request.setAmount(new BigDecimal("5"));
        request.setUnit("km");
        request.setActivityDate(LocalDate.of(2026, 7, 13));

        when(emissionFactorRepository.findByCategory(CarbonCategory.TRANSPORTATION))
                .thenReturn(Optional.of(transportationFactor));
        when(carbonEntryRepository.save(any(CarbonEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        CarbonEntryResponse response = carbonEntryService.createEntry(request, testUser);

        assertEquals("TRANSPORTATION", response.getCategory());
    }

    @Test
    void createEntry_unitAlwaysDerivedFromFactor_ignoresClientUnit() {
        CarbonEntryRequest request = new CarbonEntryRequest();
        request.setCategory("TRANSPORTATION");
        request.setAmount(new BigDecimal("10"));
        request.setUnit("miles"); // client sends something wrong/irrelevant
        request.setActivityDate(LocalDate.of(2026, 7, 13));

        when(emissionFactorRepository.findByCategory(CarbonCategory.TRANSPORTATION))
                .thenReturn(Optional.of(transportationFactor));
        when(carbonEntryRepository.save(any(CarbonEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        CarbonEntryResponse response = carbonEntryService.createEntry(request, testUser);

        assertEquals("km", response.getUnit());
    }

    @Test
    void createEntry_invalidCategory_throwsInvalidCategoryException() {
        CarbonEntryRequest request = new CarbonEntryRequest();
        request.setCategory("FLYING"); // not a real enum value
        request.setAmount(new BigDecimal("10"));
        request.setUnit("km");
        request.setActivityDate(LocalDate.of(2026, 7, 13));

        assertThrows(InvalidCategoryException.class,
                () -> carbonEntryService.createEntry(request, testUser));

        verify(carbonEntryRepository, never()).save(any());
    }

    @Test
    void createEntry_noSeededFactorForCategory_throwsInvalidCategoryException() {
        CarbonEntryRequest request = new CarbonEntryRequest();
        request.setCategory("FUEL");
        request.setAmount(new BigDecimal("10"));
        request.setUnit("litres");
        request.setActivityDate(LocalDate.of(2026, 7, 13));

        when(emissionFactorRepository.findByCategory(CarbonCategory.FUEL))
                .thenReturn(Optional.empty()); // no seed row for this category

        assertThrows(InvalidCategoryException.class,
                () -> carbonEntryService.createEntry(request, testUser));

        verify(carbonEntryRepository, never()).save(any());
    }

    // --- getHistory ---

    @Test
    void getHistory_noFilters_callsFindByUserId() {
        when(carbonEntryRepository.findByUserId(1L)).thenReturn(List.of());

        carbonEntryService.getHistory(1L, null, null, null);

        verify(carbonEntryRepository, times(1)).findByUserId(1L);
        verify(carbonEntryRepository, never()).findByUserIdAndCategory(any(), any());
    }

    @Test
    void getHistory_categoryOnly_callsFindByUserIdAndCategory() {
        when(carbonEntryRepository.findByUserIdAndCategory(1L, CarbonCategory.WATER))
                .thenReturn(List.of());

        carbonEntryService.getHistory(1L, "WATER", null, null);

        verify(carbonEntryRepository, times(1))
                .findByUserIdAndCategory(1L, CarbonCategory.WATER);
        verify(carbonEntryRepository, never()).findByUserId(anyLong());
    }

    @Test
    void getHistory_dateRangeOnly_callsFindByUserIdAndActivityDateBetween() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(1L, from, to))
                .thenReturn(List.of());

        carbonEntryService.getHistory(1L, null, from, to);

        verify(carbonEntryRepository, times(1))
                .findByUserIdAndActivityDateBetween(1L, from, to);
    }

    @Test
    void getHistory_categoryAndDateRange_callsCombinedMethod() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        when(carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                1L, CarbonCategory.FOOD, from, to)).thenReturn(List.of());

        carbonEntryService.getHistory(1L, "FOOD", from, to);

        verify(carbonEntryRepository, times(1))
                .findByUserIdAndCategoryAndActivityDateBetween(1L, CarbonCategory.FOOD, from, to);
    }

    @Test
    void getHistory_emptyResult_returnsEmptyListNotError() {
        when(carbonEntryRepository.findByUserId(1L)).thenReturn(List.of());

        List<CarbonEntryResponse> result = carbonEntryService.getHistory(1L, null, null, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // --- getEntryById ---

    @Test
    void getEntryById_found_returnsResponse() {
        CarbonEntry entry = buildEntry(5L, testUser);
        when(carbonEntryRepository.findById(5L)).thenReturn(Optional.of(entry));

        CarbonEntryResponse response = carbonEntryService.getEntryById(1L, 5L);

        assertEquals(5L, response.getEntryId());
    }

    @Test
    void getEntryById_notFound_throwsCarbonEntryNotFoundException() {
        when(carbonEntryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(CarbonEntryNotFoundException.class,
                () -> carbonEntryService.getEntryById(1L, 999L));
    }

    @Test
    void getEntryById_belongsToDifferentUser_throwsCarbonEntryNotFoundException() {
        User otherUser = new User();
        otherUser.setId(2L);
        CarbonEntry entry = buildEntry(5L, otherUser); // owned by user 2, not user 1

        when(carbonEntryRepository.findById(5L)).thenReturn(Optional.of(entry));

        assertThrows(CarbonEntryNotFoundException.class,
                () -> carbonEntryService.getEntryById(1L, 5L));
    }

    // --- deleteEntry ---

    @Test
    void deleteEntry_found_deletesSuccessfully() {
        CarbonEntry entry = buildEntry(5L, testUser);
        when(carbonEntryRepository.findById(5L)).thenReturn(Optional.of(entry));

        carbonEntryService.deleteEntry(1L, 5L);

        verify(carbonEntryRepository, times(1)).delete(entry);
    }

    @Test
    void deleteEntry_notFound_throwsException_andNeverCallsDelete() {
        when(carbonEntryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(CarbonEntryNotFoundException.class,
                () -> carbonEntryService.deleteEntry(1L, 999L));

        verify(carbonEntryRepository, never()).delete(any());
    }

    // --- helper ---

    private CarbonEntry buildEntry(Long entryId, User owner) {
        CarbonEntry entry = new CarbonEntry();
        entry.setEntryId(entryId);
        entry.setUser(owner);
        entry.setEmissionFactor(transportationFactor);
        entry.setCategory(CarbonCategory.TRANSPORTATION);
        entry.setAmount(new BigDecimal("10"));
        entry.setUnit("km");
        entry.setCarbonEmission(new BigDecimal("2.10"));
        entry.setActivityDate(LocalDate.of(2026, 7, 13));
        return entry;
    }
}