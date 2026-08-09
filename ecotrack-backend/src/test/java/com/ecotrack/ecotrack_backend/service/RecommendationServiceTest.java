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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private CarbonEntryRepository carbonEntryRepository;

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RecommendationRuleEngine ruleEngine;

    @InjectMocks
    private RecommendationService recommendationService;

    private User testUser;
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(USER_ID);
    }

    private CarbonEntry entry(CarbonCategory category, BigDecimal amount, BigDecimal emission) {
        CarbonEntry e = new CarbonEntry();
        e.setCategory(category);
        e.setAmount(amount);
        e.setCarbonEmission(emission);
        e.setActivityDate(LocalDate.now());
        return e;
    }

    private void stubSaveAllReturnsInput() {
        when(recommendationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubUserReference() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(testUser);
    }

    // --- Decision B: first-ever GET with no prior refresh auto-computes and persists ---

    @Test
    void getRecommendations_noExistingRows_computesAndPersists() {
        when(recommendationRepository.findByUserIdOrderByImpactEstimateDesc(USER_ID))
                .thenReturn(List.of());

        List<CarbonEntry> entries = List.of(
                entry(CarbonCategory.TRANSPORTATION, new BigDecimal("200"), new BigDecimal("42.00")),
                entry(CarbonCategory.ELECTRICITY, new BigDecimal("500"), new BigDecimal("30.00")),
                entry(CarbonCategory.FUEL, new BigDecimal("70"), new BigDecimal("18.00"))
        );
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(entries);

        stubUserReference();
        stubSaveAllReturnsInput();

        RecommendationResponse response = recommendationService.getRecommendations(USER_ID);

        assertNotNull(response);
        assertNull(response.getMessage());
        assertEquals(3, response.getTopRecommendations().size());
        verify(recommendationRepository, times(1)).saveAll(any());
        verify(recommendationRepository, times(1)).deleteByUserId(USER_ID);
    }

    // --- Cached path: existing rows present, no recompute/persist triggered ---

    @Test
    void getRecommendations_existingRows_returnsCachedTopWithoutRecomputing() {
        Recommendation existing = new Recommendation();
        existing.setCategory(CarbonCategory.ELECTRICITY);
        existing.setSuggestionText("cached tip 1 | cached tip 2 | cached tip 3");
        existing.setImpactEstimate(new BigDecimal("25.00"));

        when(recommendationRepository.findByUserIdOrderByImpactEstimateDesc(USER_ID))
                .thenReturn(List.of(existing));
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(entry(CarbonCategory.ELECTRICITY, new BigDecimal("500"), new BigDecimal("25.00"))));

        RecommendationResponse response = recommendationService.getRecommendations(USER_ID);

        assertEquals(1, response.getTopRecommendations().size());
        assertEquals("ELECTRICITY", response.getTopRecommendations().get(0).getCategory());
        verify(recommendationRepository, never()).saveAll(any());
        verify(recommendationRepository, never()).deleteByUserId(anyLong());
    }

    // --- Zero entries in window: fallback response, no crash ---

    @Test
    void refreshRecommendations_zeroEntriesInWindow_returnsFallbackMessage_noCrash() {
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of());

        RecommendationResponse response = assertDoesNotThrow(
                () -> recommendationService.refreshRecommendations(USER_ID));

        assertNotNull(response.getMessage());
        assertTrue(response.getTopRecommendations().isEmpty());
        assertEquals(6, response.getAllCategoriesSorted().size());
        for (CategoryBreakdownDto dto : response.getAllCategoriesSorted()) {
            assertEquals(0, BigDecimal.ZERO.compareTo(dto.getPercentage()));
        }
        verify(recommendationRepository, times(1)).deleteByUserId(USER_ID);
        verify(recommendationRepository, never()).saveAll(any());
    }

    // --- Decision A: refresh deletes old rows before inserting new ones, each call ---

    @Test
    void refreshRecommendations_deletesBeforeInsertingNewOnes_eachRefresh() {
        stubUserReference();
        stubSaveAllReturnsInput();

        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(
                        entry(CarbonCategory.TRANSPORTATION, new BigDecimal("200"), new BigDecimal("40.00")),
                        entry(CarbonCategory.ELECTRICITY, new BigDecimal("400"), new BigDecimal("20.00")),
                        entry(CarbonCategory.FOOD, new BigDecimal("60"), new BigDecimal("10.00"))
                ));

        RecommendationResponse first = recommendationService.refreshRecommendations(USER_ID);
        assertEquals(3, first.getTopRecommendations().size());

        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(
                        entry(CarbonCategory.WATER, new BigDecimal("8000"), new BigDecimal("15.00")),
                        entry(CarbonCategory.FUEL, new BigDecimal("70"), new BigDecimal("12.00"))
                ));

        RecommendationResponse second = recommendationService.refreshRecommendations(USER_ID);

        InOrder inOrder = inOrder(recommendationRepository);
        inOrder.verify(recommendationRepository, times(2)).deleteByUserId(USER_ID);
        verify(recommendationRepository, times(2)).saveAll(any());

        assertNotEquals(
                first.getTopRecommendations().stream().map(TopRecommendationDto::getCategory).toList(),
                second.getTopRecommendations().stream().map(TopRecommendationDto::getCategory).toList()
        );
    }

    // --- Fewer than 3 nonzero categories: never padded with a zero-contribution category ---

    @Test
    void refreshRecommendations_fewerThanThreeNonzeroCategories_returnsOnlyThose() {
        stubUserReference();
        stubSaveAllReturnsInput();

        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(
                        entry(CarbonCategory.TRANSPORTATION, new BigDecimal("150"), new BigDecimal("30.00")),
                        entry(CarbonCategory.ELECTRICITY, new BigDecimal("300"), new BigDecimal("15.00"))
                ));

        RecommendationResponse response = recommendationService.refreshRecommendations(USER_ID);

        assertEquals(2, response.getTopRecommendations().size());
        List<String> categories = response.getTopRecommendations().stream()
                .map(TopRecommendationDto::getCategory)
                .toList();
        assertTrue(categories.contains("TRANSPORTATION"));
        assertTrue(categories.contains("ELECTRICITY"));
    }

    // --- Tie in contribution: both categories included, no crash, deterministic size ---

    @Test
    void refreshRecommendations_tieInContribution_bothCategoriesIncluded() {
        stubUserReference();
        stubSaveAllReturnsInput();

        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(
                        entry(CarbonCategory.TRANSPORTATION, new BigDecimal("100"), new BigDecimal("20.00")),
                        entry(CarbonCategory.ELECTRICITY, new BigDecimal("200"), new BigDecimal("20.00"))
                ));

        RecommendationResponse response = recommendationService.refreshRecommendations(USER_ID);

        assertEquals(2, response.getTopRecommendations().size());
        List<String> categories = response.getTopRecommendations().stream()
                .map(TopRecommendationDto::getCategory)
                .toList();
        assertTrue(categories.contains("TRANSPORTATION"));
        assertTrue(categories.contains("ELECTRICITY"));
    }

    // --- impactEstimate reflects carbonEmission contribution, not raw amount ---

    @Test
    void refreshRecommendations_impactEstimateUsesCarbonEmission_notAmount() {
        stubUserReference();
        stubSaveAllReturnsInput();

        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(
                        entry(CarbonCategory.TRANSPORTATION, new BigDecimal("9999"), new BigDecimal("5.00")),
                        entry(CarbonCategory.ELECTRICITY, new BigDecimal("1"), new BigDecimal("50.00"))
                ));

        RecommendationResponse response = recommendationService.refreshRecommendations(USER_ID);

        // ELECTRICITY has far higher carbonEmission despite a tiny `amount`,
        // so it must rank first — proving ranking is emission-based, not amount-based.
        assertEquals("ELECTRICITY", response.getTopRecommendations().get(0).getCategory());
        assertEquals(0, new BigDecimal("50.00").compareTo(response.getTopRecommendations().get(0).getImpactEstimate()));
    }
}