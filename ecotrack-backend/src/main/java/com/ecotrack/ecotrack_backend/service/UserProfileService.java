package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.UserBadgeResponse;
import com.ecotrack.ecotrack_backend.dto.UserProfileDto;
import com.ecotrack.ecotrack_backend.entity.Badge;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.entity.UserBadge;
import com.ecotrack.ecotrack_backend.entity.UserChallenge;
import com.ecotrack.ecotrack_backend.entity.UserChallengeStatus;
import com.ecotrack.ecotrack_backend.repository.*;
import com.ecotrack.ecotrack_backend.util.GamificationUtils;
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
    private final CarbonEntryRepository carbonEntryRepository;

    public UserProfileService(
            UserRepository userRepository,
            BadgeRepository badgeRepository,
            UserBadgeRepository userBadgeRepository,
            GoalRepository goalRepository,
            UserChallengeRepository userChallengeRepository,
            CarbonEntryRepository carbonEntryRepository) {
        this.userRepository = userRepository;
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.goalRepository = goalRepository;
        this.userChallengeRepository = userChallengeRepository;
        this.carbonEntryRepository = carbonEntryRepository;
    }

    /**
     * Aggregates all badges actually held by a user:
     *   1. System badges the user has been explicitly awarded (currently: LEADERBOARD
     *      podium finishes recorded in user_badges — LEVEL badges are intentionally
     *      excluded here because they are derived dynamically in step 3 below; keeping
     *      both would either duplicate or silently disagree with each other).
     *   2. Dynamically derived CHALLENGE badges for every challenge the user completed.
     *   3. Dynamically derived MILESTONE (title) badges based on the user's current level.
     */
    @Transactional(readOnly = true)
    public List<UserBadgeResponse> getUserBadges(Long userId) {
        List<UserBadgeResponse> result = new ArrayList<>();

        // Fetch user to calculate milestone eligibility
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        // 1. Fetch only LEADERBOARD system badges the user has actually been awarded.
        //    (LEVEL badges live in the `badges` table too, but nothing in the app ever
        //    inserts user_badges rows for them — milestones are computed on the fly in
        //    step 3 instead, so pulling them here would only add noise / duplicates.)
        List<Badge> leaderboardBadges = badgeRepository.findByCategory("LEADERBOARD");
        List<UserBadge> unlockedBadges = userBadgeRepository.findByUserId(userId);

        Map<Long, UserBadge> userBadgeMap = unlockedBadges.stream()
                .filter(ub -> ub.getBadgeId() != null)
                .collect(Collectors.toMap(
                        UserBadge::getBadgeId,
                        ub -> ub,
                        (existing, replacement) -> existing
                ));

        for (Badge badge : leaderboardBadges) {
            UserBadge ub = badge.getBadgeId() != null ? userBadgeMap.get(badge.getBadgeId()) : null;
            if (ub == null) {
                continue; // Only report badges this user actually holds
            }

            result.add(new UserBadgeResponse(
                    badge.getBadgeId(),
                    badge.getName(),
                    badge.getDescription(),
                    badge.getCategory(),
                    badge.getIconUrl(),
                    badge.getRequiredXp(),
                    true,
                    ub.getAwardedAt()
            ));
        }

        // 2. Dynamically Append Accomplished Challenge Badges
        List<UserChallenge> completedChallenges = userChallengeRepository
                .findByUserIdAndStatusWithChallenge(userId, UserChallengeStatus.COMPLETED);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        for (UserChallenge uc : completedChallenges) {
            var challenge = uc.getChallenge();
            if (challenge == null) continue;

            String formattedDate = uc.getCompletedAt() != null 
                    ? uc.getCompletedAt().format(formatter) 
                    : "recently";

            String challengeDesc = challenge.getDescription() != null ? challenge.getDescription() : "";

            result.add(new UserBadgeResponse(
                    10000L + (uc.getId() != null ? uc.getId() : 0L),
                    challenge.getTitle() + " Badge",
                    String.format("Badge gained on accomplishing %s on %s. %s", challenge.getTitle(), formattedDate, challengeDesc).trim(),
                    "CHALLENGE",
                    challenge.getBadgeImageUrl() != null && !challenge.getBadgeImageUrl().isBlank()
                            ? challenge.getBadgeImageUrl() 
                            : "/badges/ch_1.avif",
                    challenge.getRewardXp(),
                    true,
                    uc.getCompletedAt()
            ));
        }

        // 3. Dynamically Append Milestone (Title) Badges — single source of truth for levels
        result.addAll(GamificationUtils.getEligibleMilestoneBadges(user));

        return result;
    }

    @Transactional(readOnly = true)
    public UserProfileDto getUserProfileByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
        return getUserProfile(user.getId());
    }

    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

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

        // --- Profile Stat Calculations ---
        long goalsPassed = goalRepository.countByUserIdAndStatusIn(
                userId, Arrays.asList("COMPLETED", "completed", "PASSED", "passed")
        );
        long activeGoals = goalRepository.countByUserIdAndStatusIn(
                userId, Arrays.asList("ACTIVE", "active", "IN_PROGRESS", "in_progress", "PENDING", "pending")
        );
        long challengesPassed = userChallengeRepository.countByUserIdAndStatus(
                userId, UserChallengeStatus.COMPLETED
        );
        long activeChallenges = userChallengeRepository.countByUserIdAndStatus(
                userId, UserChallengeStatus.IN_PROGRESS
        );
        long totalEntries = carbonEntryRepository.countByUserId(userId);

        long goalsFailed = goalRepository.countByUserIdAndStatusIn(
                userId, Arrays.asList("EXPIRED", "expired", "FAILED", "failed", "CANCELLED", "cancelled")
        );
        long challengesFailed = userChallengeRepository.countByUserIdAndStatusIn(
                userId, Arrays.asList(UserChallengeStatus.EXPIRED, UserChallengeStatus.CANCELLED)
        );

        dto.setGoalsPassed(goalsPassed);
        dto.setActiveGoals(activeGoals);
        dto.setChallengesPassed(challengesPassed);
        dto.setActiveChallenges(activeChallenges);
        dto.setTotalEntries(totalEntries);
        dto.setGoalsFailed(goalsFailed);
        dto.setChallengesFailed(challengesFailed);

        // --- Badges Section ---
        List<UserBadgeResponse> rawBadges = getUserBadges(userId);
        List<UserProfileDto.BadgeDto> badgeDtos = rawBadges.stream().map(b -> {
            UserProfileDto.BadgeDto bDto = new UserProfileDto.BadgeDto();
            bDto.setBadgeId(b.getBadgeId());
            bDto.setName(b.getName());
            bDto.setDescription(b.getDescription());
            bDto.setCategory(b.getCategory());
            bDto.setIconUrl(b.getIconUrl());
            bDto.setRequiredXp(b.getRequiredXp());
            bDto.setRequiredLevel(b.getRequiredLevel());
            bDto.setUnlocked(b.isUnlocked());
            bDto.setAwardedAt(b.getAwardedAt());
            return bDto;
        }).collect(Collectors.toList());

        dto.setBadges(badgeDtos);
        return dto;
    }

    @Transactional
    public String updateUsernameByEmail(String email, String newName) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
        user.setName(newName);
        userRepository.save(user);
        return user.getName();
    }

    @Transactional
    public String updateAvatarByEmail(String email, String avatarUrl) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        return user.getAvatarUrl();
    }

    @Transactional
    public String updateAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        return user.getAvatarUrl();
    }

    @Transactional
    public void deleteAccountByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
        userRepository.delete(user);
    }
}