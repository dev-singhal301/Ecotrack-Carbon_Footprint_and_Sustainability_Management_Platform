package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.UserProfileDto;
import com.ecotrack.ecotrack_backend.entity.Badge;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.entity.UserBadge;
import com.ecotrack.ecotrack_backend.entity.UserChallenge;
import com.ecotrack.ecotrack_backend.entity.UserChallengeStatus;
import com.ecotrack.ecotrack_backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
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

        // 1. Fetch System Badges (LEVEL & LEADERBOARD)
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

        // 2. Dynamically Append Accomplished Challenge Badges
        List<UserChallenge> completedChallenges = userChallengeRepository
                .findByUserIdAndStatusWithChallenge(userId, UserChallengeStatus.COMPLETED);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        for (UserChallenge uc : completedChallenges) {
            var challenge = uc.getChallenge();
            String formattedDate = uc.getCompletedAt() != null 
                    ? uc.getCompletedAt().format(formatter) 
                    : "recently";

            String challengeDesc = challenge.getDescription() != null ? challenge.getDescription() : "";

            UserProfileDto.BadgeDto cBadge = new UserProfileDto.BadgeDto();
            cBadge.setBadgeId(10000L + uc.getId()); // Using getId() instead of getUserChallengeId()
            cBadge.setName(challenge.getTitle() + " Badge");
            cBadge.setDescription(String.format(
                    "Badge gained on accomplishing %s on %s. %s",
                    challenge.getTitle(),
                    formattedDate,
                    challengeDesc
            ).trim());
            cBadge.setCategory("CHALLENGE");
            cBadge.setIconUrl(challenge.getBadgeImageUrl() != null && !challenge.getBadgeImageUrl().isBlank()
                    ? challenge.getBadgeImageUrl() 
                    : "/assets/badges/ch_1.avif");
            cBadge.setRequiredXp(challenge.getRewardXp());
            cBadge.setUnlocked(true);
            cBadge.setAwardedAt(uc.getCompletedAt());

            badgeDtos.add(cBadge);
        }

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