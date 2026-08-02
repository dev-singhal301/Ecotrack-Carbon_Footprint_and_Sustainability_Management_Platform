package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.UserChallengeResponse;
import com.ecotrack.ecotrack_backend.entity.*;
import com.ecotrack.ecotrack_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserChallengeServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private UserChallengeRepository userChallengeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private XpTransactionRepository xpTransactionRepository;

    @InjectMocks
    private UserChallengeService userChallengeService;

    private User testUser;
    private Challenge testChallenge;
    private UserChallenge testUserChallenge;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@ecotrack.com");
        testUser.setCurrentXp(100);
    
        testChallenge = new Challenge();
        testChallenge.setChallengeId(10L);
        testChallenge.setTitle("Reduce Meat Consumption");
        testChallenge.setRewardXp(150);
        testChallenge.setCancelled(false);
        testChallenge.setStartDate(java.time.LocalDate.now().minusDays(1)); // Added
        testChallenge.setEndDate(java.time.LocalDate.now().plusDays(7));     // Added
    
        testUserChallenge = new UserChallenge();
        testUserChallenge.setId(100L);
        testUserChallenge.setUser(testUser);
        testUserChallenge.setChallenge(testChallenge);
        testUserChallenge.setStatus(UserChallengeStatus.IN_PROGRESS);
        testUserChallenge.setCurrentValue(new BigDecimal("5"));
        testUserChallenge.setCompletionPercentage(new BigDecimal("100"));
    }

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

    @Test
    @DisplayName("joinChallenge: successfully joins active challenge")
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
    @DisplayName("joinChallenge: throws exception when already joined")
    void joinChallenge_alreadyJoined_throwsException() {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(challengeRepository.findById(testChallenge.getChallengeId())).thenReturn(Optional.of(testChallenge));
        when(userChallengeRepository.existsByUserIdAndChallengeId(testUser.getId(), testChallenge.getChallengeId())).thenReturn(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> userChallengeService.joinChallenge(testUser.getEmail(), testChallenge.getChallengeId()));

        assertEquals("User has already joined this challenge", exception.getMessage());
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
    @DisplayName("claimReward: successfully claims XP, creates transaction, and marks completed")
    void claimReward_success() {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userChallengeRepository.findByUserIdAndChallengeId(testUser.getId(), testChallenge.getChallengeId()))
                .thenReturn(Optional.of(testUserChallenge));
        when(userChallengeRepository.save(any(UserChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserChallengeResponse response = userChallengeService.claimReward(testUser.getEmail(), testChallenge.getChallengeId());

        assertEquals("COMPLETED", response.getUserStatus());
        assertEquals(250, testUser.getCurrentXp()); // 100 original + 150 reward

        // Verify XpTransaction details
        ArgumentCaptor<XpTransaction> xpCaptor = ArgumentCaptor.forClass(XpTransaction.class);
        verify(xpTransactionRepository).save(xpCaptor.capture());
        XpTransaction savedTx = xpCaptor.getValue();

        assertEquals(testUser.getId(), savedTx.getUserId());
        assertEquals(150, savedTx.getXpAmount());
        assertEquals("CHALLENGE", savedTx.getSourceType());
        assertEquals(testChallenge.getChallengeId(), savedTx.getSourceId());
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
        verify(xpTransactionRepository, never()).save(any());
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
        verify(xpTransactionRepository, never()).save(any());
    }
}