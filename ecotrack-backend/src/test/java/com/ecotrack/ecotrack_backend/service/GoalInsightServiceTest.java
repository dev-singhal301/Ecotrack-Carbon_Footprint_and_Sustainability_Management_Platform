package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.GoalInsightDetail;
import com.ecotrack.ecotrack_backend.dto.GoalInsightsBatchResponse;
import com.ecotrack.ecotrack_backend.dto.GoalProgressResponse;
import com.ecotrack.ecotrack_backend.dto.GoalInsightResponse;
import com.ecotrack.ecotrack_backend.dto.GoalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalInsightServiceTest {

    @Mock
    private GoalService goalService;

    @Mock
    private AiTipService aiTipService;

    // Real, not mocked — deterministic, dependency-free, and the whole
    // point of these tests is verifying the actual status-derivation logic,
    // not a stubbed stand-in for it.
    private GoalInsightRuleEngine ruleEngine;

    private GoalInsightService goalInsightService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        ruleEngine = new GoalInsightRuleEngine();
        goalInsightService = new GoalInsightService(goalService, ruleEngine, aiTipService);
    }

    // --- helpers ---

    private GoalResponse goal(Long id, String type, String status, LocalDate start, LocalDate end, BigDecimal target) {
        return new GoalResponse(id, "Goal " + id, type, target, BigDecimal.ZERO, 50, 50, status, start, end);
    }

    private GoalProgressResponse progress(Long goalId, BigDecimal current, BigDecimal target, BigDecimal pct, boolean completed) {
        return new GoalProgressResponse(goalId, current, target, BigDecimal.ZERO, pct, completed, LocalDateTime.now());
    }

    /** Mirrors AiTipService's real disabled/failure behavior: pass fallback straight through. */
    private void stubAiTipEchoesFallback() {
        when(aiTipService.generateGoalInsights(any())).thenAnswer(inv -> {
            List<AiTipService.GoalInsightCandidate> candidates = inv.getArgument(0);
            return candidates.stream().map(AiTipService.GoalInsightCandidate::fallback).toList();
        });
    }

    // --- empty case ---

    @Test
    @DisplayName("No goals at all: returns empty response, never calls progress or AI")
    void getInsights_noGoals_returnsEmptyResponse() {
        when(goalService.getGoalsForUser(USER_ID)).thenReturn(List.of());

        GoalInsightsBatchResponse response = goalInsightService.getInsights(USER_ID);

        assertNotNull(response);
        assertTrue(response.insights().isEmpty());
        verify(goalService, never()).calculateProgress(anyLong(), anyLong());
        verify(aiTipService, never()).generateGoalInsights(any());
    }

    // --- active goal status derivation ---

    @Test
    @DisplayName("Active goal, usage below expected pace: derives ON_TRACK")
    void getInsights_activeGoalOnTrack_derivesOnTrackStatus() {
        LocalDate start = LocalDate.now().minusDays(10);
        LocalDate end = LocalDate.now().plusDays(10); // 20-day window, 10 elapsed -> 50% expected
        GoalResponse g = goal(1L, "REDUCE_ELECTRICITY_CONSUMPTION", "ACTIVE", start, end, new BigDecimal("100"));

        when(goalService.getGoalsForUser(USER_ID)).thenReturn(List.of(g));
        when(goalService.calculateProgress(USER_ID, 1L))
                .thenReturn(progress(1L, new BigDecimal("40"), new BigDecimal("100"), new BigDecimal("40"), false));
        stubAiTipEchoesFallback();

        GoalInsightsBatchResponse response = goalInsightService.getInsights(USER_ID);

        assertEquals(1, response.insights().size());
        GoalInsightResponse insight = response.insights().get(0);
        assertEquals("ON_TRACK", insight.insight().status());
        assertEquals("STANDARD", insight.insight().source()); // fallback source, confirms rule engine path
    }

    @Test
    @DisplayName("Active goal, usage ahead of expected pace but under 100%: derives AT_RISK")
    void getInsights_activeGoalAtRisk_derivesAtRiskStatus() {
        LocalDate start = LocalDate.now().minusDays(10);
        LocalDate end = LocalDate.now().plusDays(10); // 50% expected
        GoalResponse g = goal(2L, "REDUCE_WATER_USAGE", "ACTIVE", start, end, new BigDecimal("100"));

        when(goalService.getGoalsForUser(USER_ID)).thenReturn(List.of(g));
        when(goalService.calculateProgress(USER_ID, 2L))
                .thenReturn(progress(2L, new BigDecimal("60"), new BigDecimal("100"), new BigDecimal("60"), false));
        stubAiTipEchoesFallback();

        GoalInsightsBatchResponse response = goalInsightService.getInsights(USER_ID);

        assertEquals("AT_RISK", response.insights().get(0).insight().status());
    }

    @Test
    @DisplayName("Active goal at or over 100% completion before end date: derives OVER_BUDGET")
    void getInsights_activeGoalAtCap_derivesOverBudgetStatus() {
        LocalDate start = LocalDate.now().minusDays(5);
        LocalDate end = LocalDate.now().plusDays(5);
        GoalResponse g = goal(3L, "REDUCE_FUEL_CONSUMPTION", "ACTIVE", start, end, new BigDecimal("50"));

        when(goalService.getGoalsForUser(USER_ID)).thenReturn(List.of(g));
        when(goalService.calculateProgress(USER_ID, 3L))
                .thenReturn(progress(3L, new BigDecimal("55"), new BigDecimal("50"), new BigDecimal("100"), false));
        stubAiTipEchoesFallback();

        GoalInsightsBatchResponse response = goalInsightService.getInsights(USER_ID);

        assertEquals("OVER_BUDGET", response.insights().get(0).insight().status());
    }

    // --- ended goal: the actual bug-fix regression tests ---

    @Test
    @DisplayName("Ended goal past its end date with a STALE completed=false flag: still derives COMPLETED from value comparison, not the stale flag")
    void getInsights_endedGoalPastEndDate_derivesCompletedFromValueComparison_ignoringStaleFlag() {
        LocalDate start = LocalDate.now().minusDays(35);
        LocalDate end = LocalDate.now().minusDays(5); // already past
        GoalResponse g = goal(4L, "REDUCE_ELECTRICITY_CONSUMPTION", "EXPIRED", start, end, new BigDecimal("100"));

        when(goalService.getGoalsForUser(USER_ID)).thenReturn(List.of(g));
        // completed=false is deliberately stale here — GoalService only
        // updates this flag for ACTIVE goals, so an EXPIRED goal can carry
        // an out-of-date false even though it actually finished under budget.
        when(goalService.calculateProgress(USER_ID, 4L))
                .thenReturn(progress(4L, new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("50"), false));
        stubAiTipEchoesFallback();

        GoalInsightsBatchResponse response = goalInsightService.getInsights(USER_ID);

        assertEquals(1, response.insights().size());
        assertEquals("COMPLETED", response.insights().get(0).insight().status());
    }

    @Test
    @DisplayName("Ended goal past its end date, over target: derives FAILED regardless of stale completed flag")
    void getInsights_endedGoalPastEndDate_overTarget_derivesFailedStatus() {
        LocalDate start = LocalDate.now().minusDays(35);
        LocalDate end = LocalDate.now().minusDays(5);
        GoalResponse g = goal(5L, "REDUCE_WASTE_GENERATION", "EXPIRED", start, end, new BigDecimal("100"));

        when(goalService.getGoalsForUser(USER_ID)).thenReturn(List.of(g));
        when(goalService.calculateProgress(USER_ID, 5L))
                .thenReturn(progress(5L, new BigDecimal("150"), new BigDecimal("100"), new BigDecimal("100"), false));
        stubAiTipEchoesFallback();

        GoalInsightsBatchResponse response = goalInsightService.getInsights(USER_ID);

        assertEquals("FAILED", response.insights().get(0).insight().status());
    }

    // --- ended goal cap + ordering ---

    @Test
    @DisplayName("More than 7 ended goals: only the 7 most recent (by end date desc) are included")
    void getInsights_endedGoalsExceedingCap_limitsToSevenMostRecentInDescOrder() {
        List<GoalResponse> endedGoals = new java.util.ArrayList<>();
        for (long i = 1; i <= 9; i++) {
            Long id = 100 + i;
            LocalDate end = LocalDate.now().minusDays(i); // i=1 is most recent
            endedGoals.add(goal(id, "REDUCE_CARBON_EMISSIONS", "COMPLETED",
                    end.minusDays(30), end, new BigDecimal("100")));
        }

        when(goalService.getGoalsForUser(USER_ID)).thenReturn(endedGoals);
        when(goalService.calculateProgress(eq(USER_ID), anyLong())).thenAnswer(inv -> {
            Long goalId = inv.getArgument(1);
            return progress(goalId, new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("10"), true);
        });
        stubAiTipEchoesFallback();

        GoalInsightsBatchResponse response = goalInsightService.getInsights(USER_ID);

        assertEquals(7, response.insights().size());
        List<Long> resultIds = response.insights().stream().map(GoalInsightResponse::goalId).toList();
        List<Long> expectedIds = List.of(101L, 102L, 103L, 104L, 105L, 106L, 107L); // i=1..7, most recent first
        assertEquals(expectedIds, resultIds);
    }

    // --- resilience: one goal's progress calculation failing shouldn't sink the batch ---

    @Test
    @DisplayName("One goal's calculateProgress throws: that goal is skipped, others still processed")
    void getInsights_oneGoalProgressThrows_skipsItButProcessesOthers() {
        LocalDate start = LocalDate.now().minusDays(5);
        LocalDate end = LocalDate.now().plusDays(5);
        GoalResponse healthy = goal(6L, "REDUCE_FOOD_EMISSIONS", "ACTIVE", start, end, new BigDecimal("100"));
        GoalResponse broken = goal(7L, "REDUCE_TRANSPORTATION_EMISSIONS", "ACTIVE", start, end, new BigDecimal("100"));

        when(goalService.getGoalsForUser(USER_ID)).thenReturn(List.of(healthy, broken));
        when(goalService.calculateProgress(USER_ID, 6L))
                .thenReturn(progress(6L, new BigDecimal("20"), new BigDecimal("100"), new BigDecimal("20"), false));
        when(goalService.calculateProgress(USER_ID, 7L))
                .thenThrow(new IllegalStateException("simulated failure"));
        stubAiTipEchoesFallback();

        GoalInsightsBatchResponse response = goalInsightService.getInsights(USER_ID);

        assertEquals(1, response.insights().size());
        assertEquals(6L, response.insights().get(0).goalId());

        // Confirms the candidate list handed to AI stayed in sync with the
        // skip — no null/placeholder slot for the broken goal.
        verify(aiTipService).generateGoalInsights(argThat(candidates -> candidates.size() == 1));
    }

    // --- AI-sourced details flow through correctly, not just the fallback echo ---

    @Test
    @DisplayName("AI-sourced insight details are used verbatim in the response, correctly matched to their goal by position")
    void getInsights_aiSourcedDetails_mapToCorrectGoalsInOrder() {
        LocalDate start = LocalDate.now().minusDays(5);
        LocalDate end = LocalDate.now().plusDays(5);
        GoalResponse g1 = goal(8L, "REDUCE_ELECTRICITY_CONSUMPTION", "ACTIVE", start, end, new BigDecimal("100"));
        GoalResponse g2 = goal(9L, "REDUCE_WATER_USAGE", "ACTIVE", start, end, new BigDecimal("100"));

        when(goalService.getGoalsForUser(USER_ID)).thenReturn(List.of(g1, g2));
        when(goalService.calculateProgress(USER_ID, 8L))
                .thenReturn(progress(8L, new BigDecimal("20"), new BigDecimal("100"), new BigDecimal("20"), false));
        when(goalService.calculateProgress(USER_ID, 9L))
                .thenReturn(progress(9L, new BigDecimal("30"), new BigDecimal("100"), new BigDecimal("30"), false));

        GoalInsightDetail aiDetailForG1 = new GoalInsightDetail("ON_TRACK", "AI message for goal 8", "AI action for goal 8", "AI");
        GoalInsightDetail aiDetailForG2 = new GoalInsightDetail("ON_TRACK", "AI message for goal 9", "AI action for goal 9", "AI");
        when(aiTipService.generateGoalInsights(any())).thenReturn(List.of(aiDetailForG1, aiDetailForG2));

        GoalInsightsBatchResponse response = goalInsightService.getInsights(USER_ID);

        assertEquals(2, response.insights().size());
        assertEquals(8L, response.insights().get(0).goalId());
        assertEquals("AI message for goal 8", response.insights().get(0).insight().message());
        assertEquals("AI", response.insights().get(0).insight().source());

        assertEquals(9L, response.insights().get(1).goalId());
        assertEquals("AI message for goal 9", response.insights().get(1).insight().message());
    }
}