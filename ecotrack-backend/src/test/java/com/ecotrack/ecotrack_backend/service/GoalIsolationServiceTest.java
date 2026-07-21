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
import com.ecotrack.ecotrack_backend.service.GoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private Long userId = 1L;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(userId);
        mockUser.setName("Test User");
        mockUser.setEmail("test@ecotrack.com");
    }

    @Test
    @DisplayName("Create Goal: Should accurately capture the max existing entry ID anchor token")
    void testCreateGoalCapturesMaxEntryIdAnchor() {
        // Arrange
        GoalRequest request = new GoalRequest();
        request.setGoalName("Reduce Electricity");
        request.setGoalType("REDUCE_ELECTRICITY_CONSUMPTION");
        request.setTargetValue(new BigDecimal("100.00"));
        request.setDurationDays(30);
        //request.setBaselinePeriodDays(30);
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

        // Act
        GoalResponse response = goalService.createGoal(userId, request);

        // Assert
        assertNotNull(response);
        verify(goalRepository).save(argThat(goal -> {
            assertEquals(25L, goal.getStartEntryId());
            return true;
        }));
    }

    @Test
    @DisplayName("Calculate Progress: Should filter out historical logs before the anchor and include new logs")
    void testCalculateProgressIsolatesHistoricalData() {
        // Arrange
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

        // Act
        GoalProgressResponse response = goalService.calculateProgress(userId, goalId);

        // Assert
        assertNotNull(response);
        assertEquals(0, new BigDecimal("30.00").compareTo(response.getCurrentValue()));
        assertEquals(0, new BigDecimal("30.00").compareTo(mockProgress.getCurrentValue()));
        assertFalse(response.isCompleted());
    }

    @Test
    @DisplayName("Calculate Progress: Should trigger automatic expiration transition when target budget is breached")
    void testCalculateProgressTriggersAutoBreach() {
        // Arrange
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

        // Act
        GoalProgressResponse response = goalService.calculateProgress(userId, goalId);

        // Assert
        assertNotNull(response);
        assertEquals("EXPIRED", mockGoal.getStatus());
        assertFalse(response.isCompleted());
        verify(goalRepository).save(mockGoal);
    }
}