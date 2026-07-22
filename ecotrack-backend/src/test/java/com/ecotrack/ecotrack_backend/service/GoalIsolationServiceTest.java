package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.GoalProgressResponse;
import com.ecotrack.ecotrack_backend.dto.GoalRequest;
import com.ecotrack.ecotrack_backend.dto.GoalResponse;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GoalIsolationServiceTest {

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

    private User mockUser;
    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(userId);
        mockUser.setName("Test User");
        mockUser.setEmail("test@ecotrack.com");
        mockUser.setCurrentXp(500);
        mockUser.setRewardPoints(100);
    }

    @Test
    @DisplayName("Create Goal: Should accurately capture the max existing entry ID anchor token")
    void testCreateGoalCapturesMaxEntryIdAnchor() {
        GoalRequest request = new GoalRequest();
        request.setGoalName("Reduce Electricity");
        request.setGoalType("REDUCE_ELECTRICITY_CONSUMPTION");
        request.setTargetValue(new BigDecimal("100.00"));
        request.setDurationDays(30);
        request.setRewardPoints(50);

        CarbonEntry existingEntry1 = new CarbonEntry();
        existingEntry1.setEntryId(10L);
        CarbonEntry existingEntry2 = new CarbonEntry();
        existingEntry2.setEntryId(25L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(carbonEntryRepository.findByUserId(userId)).thenReturn(List.of(existingEntry1, existingEntry2));
        when(goalRepository.findByUserIdAndStatus(userId, "ACTIVE")).thenReturn(new ArrayList<>());

        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> {
            Goal savedGoal = invocation.getArgument(0);
            savedGoal.setGoalId(1L);
            return savedGoal;
        });

        GoalResponse response = goalService.createGoal(userId, request);

        assertNotNull(response);
        verify(goalRepository).save(argThat(goal -> {
            assertEquals(25L, goal.getStartEntryId());
            return true;
        }));
    }

    @Test
    @DisplayName("Calculate Progress: Should filter out historical logs before the anchor and include new logs")
    void testCalculateProgressIsolatesHistoricalData() {
        Long goalId = 1L;
        Goal mockGoal = new Goal();
        mockGoal.setGoalId(goalId);
        mockGoal.setUser(mockUser);
        mockGoal.setGoalType("REDUCE_ELECTRICITY_CONSUMPTION");
        mockGoal.setTargetValue(new BigDecimal("100.00"));
        mockGoal.setStartDate(LocalDate.now());
        mockGoal.setEndDate(LocalDate.now().plusDays(30));
        mockGoal.setStatus("ACTIVE");
        mockGoal.setStartEntryId(25L);

        GoalProgress mockProgress = new GoalProgress();
        mockProgress.setGoal(mockGoal);
        mockProgress.setCurrentValue(BigDecimal.ZERO);
        mockProgress.setCompletionPercentage(BigDecimal.ZERO);
        mockProgress.setCompleted(false);

        CarbonEntry oldHistoricalEntry = new CarbonEntry();
        oldHistoricalEntry.setEntryId(15L);
        oldHistoricalEntry.setCarbonEmission(new BigDecimal("80.00"));

        CarbonEntry newValidEntry = new CarbonEntry();
        newValidEntry.setEntryId(26L);
        newValidEntry.setCarbonEmission(new BigDecimal("30.00"));

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(mockGoal));
        when(goalProgressRepository.findByGoalGoalId(goalId)).thenReturn(Optional.of(mockProgress));

        when(carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                eq(userId), eq(CarbonCategory.ELECTRICITY), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(oldHistoricalEntry, newValidEntry));

        GoalProgressResponse response = goalService.calculateProgress(userId, goalId);

        assertNotNull(response);
        assertEquals(0, new BigDecimal("30.00").compareTo(response.getCurrentValue()));
        assertEquals(0, new BigDecimal("30.00").compareTo(mockProgress.getCurrentValue()));
        assertFalse(response.isCompleted());
    }

    @Test
    @DisplayName("Calculate Progress: Should trigger automatic expiration transition when target budget is breached")
    void testCalculateProgressTriggersAutoBreach() {
        Long goalId = 2L;
        Goal mockGoal = new Goal();
        mockGoal.setGoalId(goalId);
        mockGoal.setUser(mockUser);
        mockGoal.setGoalType("REDUCE_ELECTRICITY_CONSUMPTION");
        mockGoal.setTargetValue(new BigDecimal("50.00"));
        mockGoal.setStartDate(LocalDate.now());
        mockGoal.setEndDate(LocalDate.now().plusDays(30));
        mockGoal.setStatus("ACTIVE");
        mockGoal.setStartEntryId(10L);

        GoalProgress mockProgress = new GoalProgress();
        mockProgress.setGoal(mockGoal);

        CarbonEntry breachEntry = new CarbonEntry();
        breachEntry.setEntryId(12L);
        breachEntry.setCarbonEmission(new BigDecimal("55.00"));

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(mockGoal));
        when(goalProgressRepository.findByGoalGoalId(goalId)).thenReturn(Optional.of(mockProgress));
        when(carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                eq(userId), eq(CarbonCategory.ELECTRICITY), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(breachEntry));

        GoalProgressResponse response = goalService.calculateProgress(userId, goalId);

        assertNotNull(response);
        assertEquals("EXPIRED", mockGoal.getStatus());
        assertFalse(response.isCompleted());
        verify(goalRepository).save(mockGoal);
    }

    // ---------------------------------------------------------------
    // XP & Reward Crediting Tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Complete Goal: Should credit XP and Reward Points to User on goal completion")
    void testCompleteGoalCreditsXpAndRewardPointsToUser() {
        Long goalId = 3L;
        Goal mockGoal = new Goal();
        mockGoal.setGoalId(goalId);
        mockGoal.setUser(mockUser);
        mockGoal.setStatus("ACTIVE");
        mockGoal.setXpReward(200);
        mockGoal.setRewardPoints(50);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(mockGoal));
        when(goalProgressRepository.findByGoalGoalId(goalId)).thenReturn(Optional.of(new GoalProgress()));

        // Act
        goalService.completeGoal(userId, goalId);

        // Verify User saved with updated XP (500 + 200 = 700) and Points (100 + 50 = 150)
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User updatedUser = userCaptor.getValue();
        assertEquals(700, updatedUser.getCurrentXp());
        assertEquals(150, updatedUser.getRewardPoints());

        // Verify Goal updated to COMPLETED
        assertEquals("COMPLETED", mockGoal.getStatus());
        verify(goalRepository).save(mockGoal);
    }

    @Test
    @DisplayName("Complete Goal: Should throw exception and prevent duplicate XP credits if goal is already completed")
    void testCompleteGoalPreventsDuplicateXpAllocation() {
        Long goalId = 4L;
        Goal completedGoal = new Goal();
        completedGoal.setGoalId(goalId);
        completedGoal.setUser(mockUser);
        completedGoal.setStatus("COMPLETED");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(completedGoal));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> goalService.completeGoal(userId, goalId));

        // Verify user was never re-saved with duplicate XP
        verify(userRepository, never()).save(any(User.class));
    }
}