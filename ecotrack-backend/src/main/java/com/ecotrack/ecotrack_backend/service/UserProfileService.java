package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.UserProfileDto;
import com.ecotrack.ecotrack_backend.entity.Badge;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.entity.UserBadge;
import com.ecotrack.ecotrack_backend.entity.UserChallengeStatus;
import com.ecotrack.ecotrack_backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final GoalRepository goalRepository;
    private final UserChallengeRepository userChallengeRepository;

    public UserProfileService(
            UserRepository userRepository,
            BadgeRepository badgeRepository,
            UserBadgeRepository userBadgeRepository,
            GoalRepository goalRepository,
            UserChallengeRepository userChallengeRepository) {
        this.userRepository = userRepository;
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.goalRepository = goalRepository;
        this.userChallengeRepository = userChallengeRepository;
    }

    public UserProfileDto getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        UserProfileDto dto = new UserProfileDto();
        dto.setUserId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setUserLevel(user.getUserLevel());
        dto.setUserRank(user.getUserRank());
        dto.setCurrentXp(user.getCurrentXp());
        dto.setNextLevelXpTarget(user.getNextLevelXpTarget());
        dto.setRewardPoints(user.getRewardPoints());
        dto.setMemberSince(user.getCreatedAt());

        // Stat Quad Calculations
        long goalsAccomplished = goalRepository.countByUserIdAndStatus(userId, "COMPLETED");
        long goalsFailed = goalRepository.countByUserIdAndStatus(userId, "EXPIRED");
        long challengesAccomplished = userChallengeRepository.countByUserIdAndStatus(userId, UserChallengeStatus.COMPLETED);
        long challengesFailed = userChallengeRepository.countByUserIdAndStatusIn(
                userId, 
                Arrays.asList(UserChallengeStatus.EXPIRED, UserChallengeStatus.CANCELLED)
        );

        dto.setGoalsAccomplished(goalsAccomplished);
        dto.setGoalsFailed(goalsFailed);
        dto.setChallengesAccomplished(challengesAccomplished);
        dto.setChallengesFailed(challengesFailed);

        // Badge Vault Mapping
        List<Badge> allBadges = badgeRepository.findAll();
        List<UserBadge> unlockedBadges = userBadgeRepository.findByUserId(userId);
        
        Map<Long, UserBadge> userBadgeMap = unlockedBadges.stream()
                .collect(Collectors.toMap(UserBadge::getBadgeId, ub -> ub));

        List<UserProfileDto.BadgeDto> badgeDtos = allBadges.stream().map(badge -> {
            boolean unlocked = userBadgeMap.containsKey(badge.getBadgeId());
            UserProfileDto.BadgeDto bDto = new UserProfileDto.BadgeDto();
            bDto.setBadgeId(badge.getBadgeId());
            bDto.setName(badge.getName());
            bDto.setDescription(badge.getDescription());
            bDto.setCategory(badge.getCategory());
            bDto.setIconUrl(badge.getIconUrl());
            bDto.setRequiredXp(badge.getRequiredXp());
            bDto.setUnlocked(unlocked);
            if (unlocked) {
                bDto.setAwardedAt(userBadgeMap.get(badge.getBadgeId()).getAwardedAt());
            }
            return bDto;
        }).collect(Collectors.toList());

        dto.setBadges(badgeDtos);
        return dto;
    }

    @Transactional
    public String updateAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        return user.getAvatarUrl();
    }
}