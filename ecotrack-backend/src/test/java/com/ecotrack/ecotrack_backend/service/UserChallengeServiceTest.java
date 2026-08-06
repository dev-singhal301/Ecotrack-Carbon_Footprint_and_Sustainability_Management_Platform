package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.ActivityLogRequest;
import com.ecotrack.ecotrack_backend.dto.UserChallengeResponse;
import com.ecotrack.ecotrack_backend.entity.*;
import com.ecotrack.ecotrack_backend.exception.DuplicateChallengeParticipationException;
import com.ecotrack.ecotrack_backend.exception.JoinWindowClosedException;
import com.ecotrack.ecotrack_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserChallengeServiceTest {

    @Mock private ChallengeRepository challengeRepository;
    @Mock private UserChallengeRepository userChallengeRepository;
    @Mock private UserRepository userRepository;
    @Mock private LeaderboardService leaderboardService;
    @Mock private CarbonEntryRepository carbonEntryRepository;
    @Mock private ChallengeActivityLogRepository activityLogRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private UserChallengeService userChallengeService;

    private User testUser;
    private Challenge testChallenge;
    private UserChallenge testUserChallenge;

    @BeforeEach
    void setUp() {
        userChallengeService = new UserChallengeService(
                challengeRepository, userChallengeRepository, userRepository,
                leaderboardService, carbonEntryRepository, activityLogRepository, eventPublisher);

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@ecotrack.com");
        testUser.setCurrentXp(100);

        testChallenge = new Challenge();
        testChallenge.setChallengeId(10L);
        testChallenge.setTitle("Reduce Meat Consumption");
        testChallenge.setRewardXp(150);
        testChallenge.setCancelled(false);
        testChallenge.setCategory("OVERALL");
        testChallenge.setTargetValue(new BigDecimal("5"));
        testChallenge.setStartDate(LocalDate.now().minusDays(1));
        testChallenge.setEndDate(LocalDate.now().plusDays(7));

        testUserChallenge = new UserChallenge();
        testUserChallenge.setId(100L);
        testUserChallenge.setUser(testUser);
        testUserChallenge.setChallenge(testChallenge);
        testUserChallenge.setStatus(UserChallengeStatus.IN_PROGRESS);
        testUserChallenge.setCurrentValue(new BigDecimal("5"));
        testUserChallenge.setCompletionPercentage(new BigDecimal("100"));
    }

    // ── getUserChallenges ──

    @Test
    @DisplayName("getUserChallenges: returns user status for joined and unjoined challenges")
    void getUserChallenges_success() {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(challengeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(testChallenge));
        when(userChallengeRepository.findByUserId(testUser.getId())).thenReturn(List.of(testUserChallenge));

        List<UserChallengeResponse> result = userChallengeService.getUserChallenges(testUser.getEmail());

        assertEquals(1, result.size());
        assertEquals("IN_PROGRESS", result.get(0).getUserStatus());
        assertEquals(testChallenge.getTitle(), result.get(0).getChallenge().getTitle());
    }

    // ── joinChallenge ──

    @Test
    @DisplayName("joinChallenge: successfully joins an activity-based challenge within the join window")
    void joinChallenge_success() {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(challengeRepository.findById(testChallenge.getChallengeId())).thenReturn(Optional.of(testChallenge));
        when(userChallengeRepository.existsByUserIdAndChallengeId(testUser.getId(), testChallenge.getChallengeId())).thenReturn(false);
        when(userChallengeRepository.save(any(UserChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserChallengeResponse response = userChallengeService.joinChallenge(testUser.getEmail(), testChallenge.getChallengeId());

        assertNotNull(response);
        assertEquals("IN_PROGRESS", response.getUserStatus());
        verify(userChallengeRepository, times(1)).save(any(UserChallenge.class));
    }

    @Test
    @DisplayName("joinChallenge: throws DuplicateChallengeParticipationException when already joined")
    void joinChallenge_alreadyJoined_throwsException() {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(challengeRepository.findById(testChallenge.getChallengeId())).thenReturn(Optional.of(testChallenge));
        when(userChallengeRepository.existsByUserIdAndChallengeId(testUser.getId(), testChallenge.getChallengeId())).thenReturn(true);

        assertThrows(DuplicateChallengeParticipationException.class,
                () -> userChallengeService.joinChallenge(testUser.getEmail(), testChallenge.getChallengeId()));

        verify(userChallengeRepository, never()).save(any());
    }

    @Test
    @DisplayName("joinChallenge: throws exception when challenge is cancelled")
    void joinChallenge_cancelled_throwsException() {
        testChallenge.setCancelled(true);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(challengeRepository.findById(testChallenge.getChallengeId())).thenReturn(Optional.of(testChallenge));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> userChallengeService.joinChallenge(testUser.getEmail(), testChallenge.getChallengeId()));

        assertEquals("Cannot join a cancelled challenge", exception.getMessage());
    }

    @Test
    @DisplayName("joinChallenge: throws JoinWindowClosedException if challenge hasn't started yet")
    void joinChallenge_beforeStart_throwsJoinWindowClosed() {
        testChallenge.setStartDate(LocalDate.now().plusDays(3));
        testChallenge.setEndDate(LocalDate.now().plusDays(10));
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(challengeRepository.findById(testChallenge.getChallengeId())).thenReturn(Optional.of(testChallenge));

        assertThrows(JoinWindowClosedException.class,
                () -> userChallengeService.joinChallenge(testUser.getEmail(), testChallenge.getChallengeId()));
    }

    @Test
    @DisplayName("joinChallenge: throws JoinWindowClosedException once more than 2 days past start")
    void joinChallenge_afterWindow_throwsJoinWindowClosed() {
        testChallenge.setStartDate(LocalDate.now().minusDays(3));
        testChallenge.setEndDate(LocalDate.now().plusDays(10));
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(challengeRepository.findById(testChallenge.getChallengeId())).thenReturn(Optional.of(testChallenge));

        assertThrows(JoinWindowClosedException.class,
                () -> userChallengeService.joinChallenge(testUser.getEmail(), testChallenge.getChallengeId()));
    }

    @Test
    @DisplayName("joinChallenge: carbon-reduction challenge freezes a scaled baseline and effective target")
    void joinChallenge_carbonReduction_computesBaseline() {
        testChallenge.setChallengeType(ChallengeType.CARBON_REDUCTION);
        testChallenge.setCategory("OVERALL");
        testChallenge.setTargetValue(new BigDecimal("20")); // 20% reduction
        testChallenge.setStartDate(LocalDate.now().minusDays(1));
        testChallenge.setEndDate(LocalDate.now().plusDays(29)); // 30 remaining incl. today boundary handled by ChronoUnit

        CarbonEntry historicalEntry = new CarbonEntry();
        historicalEntry.setEntryId(5L);
        historicalEntry.setCarbonEmission(new BigDecimal("100"));

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(challengeRepository.findById(testChallenge.getChallengeId())).thenReturn(Optional.of(testChallenge));
        when(userChallengeRepository.existsByUserIdAndChallengeId(testUser.getId(), testChallenge.getChallengeId())).thenReturn(false);
        when(carbonEntryRepository.findByUserIdAndActivityDateBetween(eq(testUser.getId()), any(), any()))
                .thenReturn(List.of(historicalEntry));
        when(carbonEntryRepository.findByUserId(testUser.getId())).thenReturn(List.of(historicalEntry));
        when(userChallengeRepository.save(any(UserChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserChallengeResponse response = userChallengeService.joinChallenge(testUser.getEmail(), testChallenge.getChallengeId());

        assertNotNull(response.getEffectiveTarget());
        assertNotNull(response.getBaselineValue());
        // baseline/target are both positive and target < baseline (a real reduction was applied)
        assertTrue(response.getBaselineValue().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(response.getEffectiveTarget().compareTo(response.getBaselineValue()) < 0);
    }

    // ── logActivity ──

    @Test
    @DisplayName("logActivity: COUNT type logs exactly once, rejects a second log the same day")
    void logActivity_countType_rejectsSecondLogSameDay() {
        testChallenge.setChallengeType(ChallengeType.ACTIVITY_BASED);
        testChallenge.setTrackingType(TrackingType.COUNT);
        testChallenge.setTargetValue(new BigDecimal("5"));
        testUserChallenge.setCurrentValue(BigDecimal.ZERO);
        testUserChallenge.setCompletionPercentage(BigDecimal.ZERO);

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userChallengeRepository.findByUserIdAndChallengeId(testUser.getId(), testChallenge.getChallengeId()))
                .thenReturn(Optional.of(testUserChallenge));
        when(activityLogRepository.existsByUserChallengeIdAndLogDate(testUserChallenge.getId(), LocalDate.now()))
                .thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> userChallengeService.logActivity(testUser.getEmail(), testChallenge.getChallengeId(), null));

        verify(activityLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("logActivity: reaching the target auto-completes and awards XP without a separate claim call")
    void logActivity_reachesTarget_autoCompletes() {
        testChallenge.setChallengeType(ChallengeType.ACTIVITY_BASED);
        testChallenge.setTrackingType(TrackingType.DISTANCE);
        testChallenge.setTargetValue(new BigDecimal("5"));
        testUserChallenge.setCurrentValue(BigDecimal.ZERO);
        testUserChallenge.setCompletionPercentage(BigDecimal.ZERO);
        testUserChallenge.setStatus(UserChallengeStatus.IN_PROGRESS);

        ActivityLogRequest request = new ActivityLogRequest();
        request.setAmount(new BigDecimal("5"));

        ChallengeActivityLog savedLog = new ChallengeActivityLog();
        savedLog.setAmount(new BigDecimal("5"));

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userChallengeRepository.findByUserIdAndChallengeId(testUser.getId(), testChallenge.getChallengeId()))
                .thenReturn(Optional.of(testUserChallenge));
        when(activityLogRepository.save(any(ChallengeActivityLog.class))).thenReturn(savedLog);
        when(activityLogRepository.findByUserChallengeId(testUserChallenge.getId())).thenReturn(List.of(savedLog));
        when(userChallengeRepository.save(any(UserChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserChallengeResponse response = userChallengeService.logActivity(
                testUser.getEmail(), testChallenge.getChallengeId(), request);

        assertEquals("COMPLETED", response.getUserStatus());
        verify(leaderboardService, times(1)).awardXp(testUser.getId(), testChallenge.getRewardXp(), "CHALLENGE", testChallenge.getChallengeId());
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    @DisplayName("logActivity: rejects carbon-reduction challenges")
    void logActivity_carbonChallenge_rejected() {
        testChallenge.setChallengeType(ChallengeType.CARBON_REDUCTION);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userChallengeRepository.findByUserIdAndChallengeId(testUser.getId(), testChallenge.getChallengeId()))
                .thenReturn(Optional.of(testUserChallenge));

        assertThrows(IllegalStateException.class,
                () -> userChallengeService.logActivity(testUser.getEmail(), testChallenge.getChallengeId(), null));

        verify(activityLogRepository, never()).save(any());
    }

    // ── claimReward ──

    @Test
    @DisplayName("claimReward: successfully claims XP and marks completed once at 100%")
    void claimReward_success() {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userChallengeRepository.findByUserIdAndChallengeId(testUser.getId(), testChallenge.getChallengeId()))
                .thenReturn(Optional.of(testUserChallenge));
        when(userChallengeRepository.save(any(UserChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserChallengeResponse response = userChallengeService.claimReward(testUser.getEmail(), testChallenge.getChallengeId());

        assertEquals("COMPLETED", response.getUserStatus());
        verify(leaderboardService, times(1)).awardXp(testUser.getId(), 150, "CHALLENGE", testChallenge.getChallengeId());
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    @DisplayName("claimReward: throws exception if progress is under 100%")
    void claimReward_incomplete_throwsException() {
        testUserChallenge.setCompletionPercentage(new BigDecimal("75.00"));

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userChallengeRepository.findByUserIdAndChallengeId(testUser.getId(), testChallenge.getChallengeId()))
                .thenReturn(Optional.of(testUserChallenge));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> userChallengeService.claimReward(testUser.getEmail(), testChallenge.getChallengeId()));

        assertEquals("Challenge is not fully completed yet", exception.getMessage());
        verify(leaderboardService, never()).awardXp(any(), any(), any(), any());
    }

    @Test
    @DisplayName("claimReward: throws exception if reward was already claimed")
    void claimReward_alreadyCompleted_throwsException() {
        testUserChallenge.setStatus(UserChallengeStatus.COMPLETED);

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userChallengeRepository.findByUserIdAndChallengeId(testUser.getId(), testChallenge.getChallengeId()))
                .thenReturn(Optional.of(testUserChallenge));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> userChallengeService.claimReward(testUser.getEmail(), testChallenge.getChallengeId()));

        assertEquals("Reward already claimed for this challenge", exception.getMessage());
        verify(leaderboardService, never()).awardXp(any(), any(), any(), any());
    }
}
