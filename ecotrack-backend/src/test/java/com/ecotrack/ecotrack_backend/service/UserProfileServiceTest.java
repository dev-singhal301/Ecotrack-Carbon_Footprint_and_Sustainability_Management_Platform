package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.UserProfileDto;
import com.ecotrack.ecotrack_backend.entity.Badge;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.entity.UserBadge;
import com.ecotrack.ecotrack_backend.entity.UserChallengeStatus;
import com.ecotrack.ecotrack_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BadgeRepository badgeRepository;

    @Mock
    private UserBadgeRepository userBadgeRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private UserChallengeRepository userChallengeRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    private User mockUser;
    private Badge mockBadge;
    private UserBadge mockUserBadge;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setName("Eco Pioneer");
        mockUser.setEmail("pioneer@ecotrack.com");
        mockUser.setAvatarUrl("https://example.com/avatar.png");
        mockUser.setUserLevel(3);
        mockUser.setUserRank("Eco Warrior");
        mockUser.setCurrentXp(150);
        mockUser.setNextLevelXpTarget(300);
        mockUser.setRewardPoints(50);

        mockBadge = new Badge();
        mockBadge.setBadgeId(10L);
        mockBadge.setName("Tree Planter");
        mockBadge.setDescription("Planted 5 trees");
        mockBadge.setCategory("FORESTRY");
        mockBadge.setIconUrl("https://example.com/badge.png");
        mockBadge.setRequiredXp(100);

        mockUserBadge = new UserBadge(1L, 10L);
    }

    @Test
    @DisplayName("getUserProfile - Successfully retrieves profile, stats, and mapped badges")
    void getUserProfile_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(goalRepository.countByUserIdAndStatus(1L, "COMPLETED")).thenReturn(5L);
        when(goalRepository.countByUserIdAndStatus(1L, "EXPIRED")).thenReturn(2L);
        when(userChallengeRepository.countByUserIdAndStatus(1L, UserChallengeStatus.COMPLETED)).thenReturn(8L);
        when(userChallengeRepository.countByUserIdAndStatusIn(eq(1L), any())).thenReturn(1L);

        when(badgeRepository.findAll()).thenReturn(List.of(mockBadge));
        when(userBadgeRepository.findByUserId(1L)).thenReturn(List.of(mockUserBadge));

        UserProfileDto profile = userProfileService.getUserProfile(1L);

        assertNotNull(profile);
        assertEquals(1L, profile.getUserId());
        assertEquals("Eco Pioneer", profile.getName());
        assertEquals("https://example.com/avatar.png", profile.getAvatarUrl());

        assertEquals(5L, profile.getGoalsAccomplished());
        assertEquals(2L, profile.getGoalsFailed());
        assertEquals(8L, profile.getChallengesAccomplished());
        assertEquals(1L, profile.getChallengesFailed());

        assertEquals(1, profile.getBadges().size());
        UserProfileDto.BadgeDto badgeDto = profile.getBadges().get(0);
        assertEquals(10L, badgeDto.getBadgeId());
        assertTrue(badgeDto.isUnlocked());

        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("getUserProfile - Throws exception when user does not exist")
    void getUserProfile_UserNotFound_ThrowsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userProfileService.getUserProfile(99L);
        });

        assertEquals("User not found with ID: 99", exception.getMessage());
        verify(userRepository, times(1)).findById(99L);
    }

    @Test
    @DisplayName("updateAvatar - Successfully updates user avatar URL")
    void updateAvatar_Success() {
        String newAvatarUrl = "https://example.com/new-avatar.png";
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        String resultUrl = userProfileService.updateAvatar(1L, newAvatarUrl);

        assertEquals(newAvatarUrl, resultUrl);
        assertEquals(newAvatarUrl, mockUser.getAvatarUrl());
        verify(userRepository, times(1)).save(mockUser);
    }

    @Test
    @DisplayName("updateAvatar - Throws exception when user does not exist")
    void updateAvatar_UserNotFound_ThrowsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            userProfileService.updateAvatar(99L, "https://example.com/avatar.png");
        });

        verify(userRepository, never()).save(any());
    }
}