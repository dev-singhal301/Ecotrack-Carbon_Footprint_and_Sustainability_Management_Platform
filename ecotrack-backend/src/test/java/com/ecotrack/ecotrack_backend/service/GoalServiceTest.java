package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.GoalRequest;
import com.ecotrack.ecotrack_backend.dto.GoalResponse;
import com.ecotrack.ecotrack_backend.dto.GoalProgressResponse;
import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.CarbonEntry;
import com.ecotrack.ecotrack_backend.entity.Goal;
import com.ecotrack.ecotrack_backend.entity.GoalProgress;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.CarbonEntryRepository;
import com.ecotrack.ecotrack_backend.repository.GoalProgressRepository;
import com.ecotrack.ecotrack_backend.repository.GoalRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private GoalProgressRepository goalProgressRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CarbonEntryRepository carbonEntryRepository;

    @InjectMocks
    private GoalService goalService;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);

        otherUser = new User();
        otherUser.setId(2L);
    }

    // --- createGoal: baseline capture ---

    @Test
    void createGoal_singleCategoryType_sumsMatchingEntriesAsBaseline() {
        GoalRequest request = buildRequest("REDUCE_TRANSPORTATION_EMISSIONS",
                new BigDecimal("20"), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15), null, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                eq(1L), eq(CarbonCategory.TRANSPORTATION), any(), any()))
                .thenReturn(List.of(
                        entryWithEmission(new BigDecimal("2.10")),
                        entryWithEmission(new BigDecimal("2.10")),
                        entryWithEmission(new BigDecimal("2.10"))));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(goalProgressRepository.save(any(GoalProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalResponse response = goalService.createGoal(1L, request);

        assertEquals(0, new BigDecimal("6.30").compareTo(response.getBaselineValue()));
    }

    @Test
    void createGoal_aggregateGoalType_sumsAcrossAllCategories_ignoresCategoryFilter() {
        GoalRequest request = buildRequest("REDUCE_CARBON_EMISSIONS",
                new BigDecimal("50"), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15), null, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(
                        entryWithEmission(new BigDecimal("9.00")),
                        entryWithEmission(new BigDecimal("0.0045"))));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(goalProgressRepository.save(any(GoalProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalResponse response = goalService.createGoal(1L, request);

        assertEquals(0, new BigDecimal("9.0045").compareTo(response.getBaselineValue()));
        verify(carbonEntryRepository, never())
                .findByUserIdAndCategoryAndActivityDateBetween(any(), any(), any(), any());
    }

    @Test
    void createGoal_noPriorEntries_baselineDefaultsToZero() {
        GoalRequest request = buildRequest("REDUCE_WATER_USAGE",
                new BigDecimal("10"), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15), null, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                eq(1L), eq(CarbonCategory.WATER), any(), any()))
                .thenReturn(List.of());
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(goalProgressRepository.save(any(GoalProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalResponse response = goalService.createGoal(1L, request);

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getBaselineValue()));
    }

    @Test
    void createGoal_customBaselinePeriodDays_isRespected() {
        GoalRequest request = buildRequest("REDUCE_WATER_USAGE",
                new BigDecimal("10"), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15), null, 7);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                eq(1L), eq(CarbonCategory.WATER),
                eq(LocalDate.of(2026, 7, 8)), eq(LocalDate.of(2026, 7, 14))))
                .thenReturn(List.of(entryWithEmission(new BigDecimal("3.00"))));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(goalProgressRepository.save(any(GoalProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalResponse response = goalService.createGoal(1L, request);

        assertEquals(0, new BigDecimal("3.00").compareTo(response.getBaselineValue()));
    }

    @Test
    void createGoal_invalidGoalType_throwsIllegalArgumentException() {
        GoalRequest request = buildRequest("PLANT_TREES",
                new BigDecimal("10"), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15), null, null);

        assertThrows(IllegalArgumentException.class, () -> goalService.createGoal(1L, request));
        verify(goalRepository, never()).save(any());
    }

    @Test
    void createGoal_endDateBeforeStartDate_throwsIllegalArgumentException() {
        GoalRequest request = buildRequest("REDUCE_WATER_USAGE",
                new BigDecimal("10"), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 7, 15), null, null);

        assertThrows(IllegalArgumentException.class, () -> goalService.createGoal(1L, request));
        verify(goalRepository, never()).save(any());
    }

    @Test
    void createGoal_userNotFound_throwsIllegalArgumentException() {
        GoalRequest request = buildRequest("REDUCE_WATER_USAGE",
                new BigDecimal("10"), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15), null, null);

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> goalService.createGoal(99L, request));
    }

    // --- calculateProgress: zero-entries guard (the core Route B requirement) ---

    @Test
    void calculateProgress_noNewEntriesSinceStart_returnsZeroPercent_notCompleted() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_TRANSPORTATION_EMISSIONS",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        GoalProgress progress = new GoalProgress();
        progress.setGoal(goal);

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        when(goalProgressRepository.findByGoalGoalId(10L)).thenReturn(Optional.of(progress));
        when(carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                eq(1L), eq(CarbonCategory.TRANSPORTATION), any(), any()))
                .thenReturn(List.of()); // no new entries logged

        GoalProgressResponse response = goalService.calculateProgress(1L, 10L);

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getCompletionPercentage()));
        assertFalse(response.isCompleted());
    }

    // --- calculateProgress: real delta math ---

    @Test
    void calculateProgress_partialReduction_computesCorrectPercentage() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_TRANSPORTATION_EMISSIONS",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        GoalProgress progress = new GoalProgress();
        progress.setGoal(goal);

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        when(goalProgressRepository.findByGoalGoalId(10L)).thenReturn(Optional.of(progress));
        when(carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                eq(1L), eq(CarbonCategory.TRANSPORTATION), any(), any()))
                .thenReturn(List.of(entryWithEmission(new BigDecimal("1.05")))); // current = 1.05

        GoalProgressResponse response = goalService.calculateProgress(1L, 10L);

        // (6.30 - 1.05) / 20 * 100 = 26.25
        assertEquals(0, new BigDecimal("26.2500").compareTo(response.getCompletionPercentage()));
        assertFalse(response.isCompleted());
    }

    @Test
    void calculateProgress_reductionMeetsOrExceedsTarget_capsAtHundredAndMarksCompleted() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_TRANSPORTATION_EMISSIONS",
                new BigDecimal("5"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        GoalProgress progress = new GoalProgress();
        progress.setGoal(goal);

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        when(goalProgressRepository.findByGoalGoalId(10L)).thenReturn(Optional.of(progress));
        when(carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                eq(1L), eq(CarbonCategory.TRANSPORTATION), any(), any()))
                .thenReturn(List.of(entryWithEmission(new BigDecimal("1.05"))));
        // (6.30 - 1.05) / 5 * 100 = 105 -> should cap at 100

        GoalProgressResponse response = goalService.calculateProgress(1L, 10L);

        assertEquals(0, new BigDecimal("100.0000").compareTo(response.getCompletionPercentage()));
        assertTrue(response.isCompleted());
    }

    @Test
    void calculateProgress_goalCompleted_updatesGoalStatusToCompleted() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_TRANSPORTATION_EMISSIONS",
                new BigDecimal("5"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        GoalProgress progress = new GoalProgress();
        progress.setGoal(goal);

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        when(goalProgressRepository.findByGoalGoalId(10L)).thenReturn(Optional.of(progress));
        when(carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                eq(1L), eq(CarbonCategory.TRANSPORTATION), any(), any()))
                .thenReturn(List.of(entryWithEmission(new BigDecimal("1.05"))));

        goalService.calculateProgress(1L, 10L);

        assertEquals("COMPLETED", goal.getStatus());
        verify(goalRepository, times(1)).save(goal);
    }

    // --- calculateProgress: emissions-went-up guard ---

    @Test
    void calculateProgress_currentExceedsBaseline_returnsZeroNotNegative() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_FUEL_CONSUMPTION",
                new BigDecimal("20"), BigDecimal.ZERO,
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        GoalProgress progress = new GoalProgress();
        progress.setGoal(goal);

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        when(goalProgressRepository.findByGoalGoalId(10L)).thenReturn(Optional.of(progress));
        when(carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                eq(1L), eq(CarbonCategory.FUEL), any(), any()))
                .thenReturn(List.of(entryWithEmission(new BigDecimal("134.00")))); // current > baseline (0)

        GoalProgressResponse response = goalService.calculateProgress(1L, 10L);

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getCompletionPercentage()));
        assertFalse(response.isCompleted());
    }

    // --- calculateProgress: aggregate category ---

    @Test
    void calculateProgress_aggregateGoalType_sumsCurrentAcrossAllCategories() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_CARBON_EMISSIONS",
                new BigDecimal("50"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        GoalProgress progress = new GoalProgress();
        progress.setGoal(goal);

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        when(goalProgressRepository.findByGoalGoalId(10L)).thenReturn(Optional.of(progress));
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(
                        entryWithEmission(new BigDecimal("9.00")),
                        entryWithEmission(new BigDecimal("0.0045")),
                        entryWithEmission(new BigDecimal("1.05")),
                        entryWithEmission(new BigDecimal("134.00"))));

        GoalProgressResponse response = goalService.calculateProgress(1L, 10L);

        assertEquals(0, new BigDecimal("144.0545").compareTo(response.getCurrentValue()));
        verify(carbonEntryRepository, never())
                .findByUserIdAndCategoryAndActivityDateBetween(any(), any(), any(), any());
    }

    // --- calculateProgress: defensive null handling ---

    @Test
    void calculateProgress_entryWithNullEmission_isSkippedNotThrown() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_TRANSPORTATION_EMISSIONS",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        GoalProgress progress = new GoalProgress();
        progress.setGoal(goal);

        // one entry with a real emission, one with a null emission (bad/incomplete data) —
        // sumEmissions() must skip the null via .filter(Objects::nonNull) rather than NPE
        CarbonEntry validEntry = entryWithEmission(new BigDecimal("1.05"));
        CarbonEntry nullEmissionEntry = new CarbonEntry(); // carbonEmission left null

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        when(goalProgressRepository.findByGoalGoalId(10L)).thenReturn(Optional.of(progress));
        when(carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                eq(1L), eq(CarbonCategory.TRANSPORTATION), any(), any()))
                .thenReturn(List.of(validEntry, nullEmissionEntry));

        GoalProgressResponse response = assertDoesNotThrow(() -> goalService.calculateProgress(1L, 10L));

        // only the valid entry's 1.05 should count; the null-emission entry contributes 0
        assertEquals(0, new BigDecimal("1.05").compareTo(response.getCurrentValue()));
    }

    // --- calculateProgress: ownership / not-found ---

    @Test
    void calculateProgress_differentUser_throwsIllegalArgumentException() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_WATER_USAGE",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

        assertThrows(IllegalArgumentException.class,
                () -> goalService.calculateProgress(2L, 10L)); // user 2 requesting user 1's goal
        verify(goalProgressRepository, never()).findByGoalGoalId(any());
    }

    @Test
    void calculateProgress_goalNotFound_throwsIllegalArgumentException() {
        when(goalRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> goalService.calculateProgress(1L, 999L));
    }

    @Test
    void calculateProgress_noProgressRecord_throwsIllegalArgumentException() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_WATER_USAGE",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        when(goalProgressRepository.findByGoalGoalId(10L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> goalService.calculateProgress(1L, 10L));
    }

    // --- getGoalById: ownership ---

    @Test
    void getGoalById_ownGoal_returnsResponse() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_WATER_USAGE",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

        GoalResponse response = goalService.getGoalById(1L, 10L);

        assertEquals(10L, response.getGoalId());
    }

    @Test
    void getGoalById_belongsToDifferentUser_throwsIllegalArgumentException() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_WATER_USAGE",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

        assertThrows(IllegalArgumentException.class, () -> goalService.getGoalById(2L, 10L));
    }

    @Test
    void getGoalById_notFound_throwsIllegalArgumentException() {
        when(goalRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> goalService.getGoalById(1L, 999L));
    }

    // --- updateGoal: ownership + field preservation ---

    @Test
    void updateGoal_omittedRewardPoints_preservesExistingValue() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_WATER_USAGE",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        goal.setRewardPoints(50);

        GoalRequest request = buildRequest("REDUCE_WATER_USAGE",
                new BigDecimal("25"), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15), null, null);
        // rewardPoints intentionally left null on the request

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalResponse response = goalService.updateGoal(1L, 10L, request);

        assertEquals(50, response.getRewardPoints());
    }

    @Test
    void updateGoal_rewardPointsProvided_overwritesExistingValue() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_WATER_USAGE",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        goal.setRewardPoints(50);

        GoalRequest request = buildRequest("REDUCE_WATER_USAGE",
                new BigDecimal("25"), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15), 75, null);

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> inv.getArgument(0));

        GoalResponse response = goalService.updateGoal(1L, 10L, request);

        assertEquals(75, response.getRewardPoints());
    }

    @Test
    void updateGoal_belongsToDifferentUser_throwsIllegalArgumentException() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_WATER_USAGE",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        GoalRequest request = buildRequest("REDUCE_WATER_USAGE",
                new BigDecimal("25"), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15), null, null);

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

        assertThrows(IllegalArgumentException.class, () -> goalService.updateGoal(2L, 10L, request));
        verify(goalRepository, never()).save(any());
    }

    @Test
    void updateGoal_invalidGoalType_throwsIllegalArgumentException() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_WATER_USAGE",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        GoalRequest request = buildRequest("PLANT_TREES",
                new BigDecimal("25"), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15), null, null);

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

        assertThrows(IllegalArgumentException.class, () -> goalService.updateGoal(1L, 10L, request));
        verify(goalRepository, never()).save(any());
    }

    @Test
    void updateGoal_endDateBeforeStartDate_throwsIllegalArgumentException() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_WATER_USAGE",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        GoalRequest request = buildRequest("REDUCE_WATER_USAGE",
                new BigDecimal("25"), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 7, 15), null, null);

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

        assertThrows(IllegalArgumentException.class, () -> goalService.updateGoal(1L, 10L, request));
        verify(goalRepository, never()).save(any());
    }

    // --- getGoalsForUser / getGoalsForUserByStatus ---

    @Test
    void getGoalsForUser_returnsOnlyThatUsersGoals() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_WATER_USAGE",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        when(goalRepository.findByUserId(1L)).thenReturn(List.of(goal));

        List<GoalResponse> results = goalService.getGoalsForUser(1L);

        assertEquals(1, results.size());
        assertEquals(10L, results.get(0).getGoalId());
    }

    @Test
    void getGoalsForUserByStatus_filtersCorrectly() {
        Goal goal = buildGoal(10L, testUser, "REDUCE_WATER_USAGE",
                new BigDecimal("20"), new BigDecimal("6.30"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15));
        when(goalRepository.findByUserIdAndStatus(1L, "ACTIVE")).thenReturn(List.of(goal));

        List<GoalResponse> results = goalService.getGoalsForUserByStatus(1L, "ACTIVE");

        assertEquals(1, results.size());
        verify(goalRepository, times(1)).findByUserIdAndStatus(1L, "ACTIVE");
    }

    // --- helpers ---

    private GoalRequest buildRequest(String goalType, BigDecimal targetValue,
            LocalDate startDate, LocalDate endDate, Integer rewardPoints, Integer baselinePeriodDays) {
        GoalRequest request = new GoalRequest();
        request.setGoalName("Test goal");
        request.setGoalType(goalType);
        request.setTargetValue(targetValue);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setRewardPoints(rewardPoints);
        request.setBaselinePeriodDays(baselinePeriodDays);
        return request;
    }

    private Goal buildGoal(Long goalId, User owner, String goalType, BigDecimal targetValue,
            BigDecimal baselineValue, LocalDate startDate, LocalDate endDate) {
        Goal goal = new Goal();
        goal.setGoalId(goalId);
        goal.setUser(owner);
        goal.setGoalName("Test goal");
        goal.setGoalType(goalType);
        goal.setTargetValue(targetValue);
        goal.setBaselineValue(baselineValue);
        goal.setStartDate(startDate);
        goal.setEndDate(endDate);
        goal.setStatus("ACTIVE");
        return goal;
    }

    private CarbonEntry entryWithEmission(BigDecimal emission) {
        CarbonEntry entry = new CarbonEntry();
        entry.setCarbonEmission(emission);
        return entry;
    }
}