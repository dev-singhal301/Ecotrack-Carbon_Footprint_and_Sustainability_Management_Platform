package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.entity.Badge;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.entity.UserBadge;
import com.ecotrack.ecotrack_backend.entity.XpTransaction;
import com.ecotrack.ecotrack_backend.repository.BadgeRepository;
import com.ecotrack.ecotrack_backend.repository.UserBadgeRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.repository.XpTransactionRepository;
import com.ecotrack.ecotrack_backend.util.GamificationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserXpService {

    private final UserRepository userRepository;
    private final XpTransactionRepository xpTransactionRepository;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;

    public UserXpService(
            UserRepository userRepository,
            XpTransactionRepository xpTransactionRepository,
            BadgeRepository badgeRepository,
            UserBadgeRepository userBadgeRepository) {
        this.userRepository = userRepository;
        this.xpTransactionRepository = xpTransactionRepository;
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
    }

    /**
     * Centralized XP Engine Method:
     * 1. Accumulates permanent XP & reward points on User entity.
     * 2. Syncs level, target XP, and rank title using GamificationUtils.
     * 3. Creates an XpTransaction record to feed the monthly leaderboard.
     * 4. Checks and awards level progression badges.
     */
    @Transactional
    public User awardXpAndPoints(Long userId, int xpAmount, int rewardPoints, String sourceType, Long sourceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (xpAmount > 0) {
            int currentXp = (user.getCurrentXp() != null) ? user.getCurrentXp() : 0;
            user.setCurrentXp(currentXp + xpAmount);

            // Delegate gamification calculations to GamificationUtils
            user.setUserLevel(GamificationUtils.getLevel(user));
            user.setNextLevelXpTarget(GamificationUtils.getNextLevelXpTarget(user));
            user.setUserRank(GamificationUtils.getRankTitle(user));

            // Save transaction audit row for Monthly Leaderboard calculations
            XpTransaction transaction = new XpTransaction(userId, xpAmount, sourceType, sourceId);
            xpTransactionRepository.save(transaction);

            // Automatically check and award Level badges based on updated XP
            checkAndAwardLevelBadges(user);
        }

        if (rewardPoints > 0) {
            int currentPoints = (user.getRewardPoints() != null) ? user.getRewardPoints() : 0;
            user.setRewardPoints(currentPoints + rewardPoints);
        }

        return userRepository.save(user);
    }

    /**
     * Automatically unlocks LEVEL badges when user's total XP satisfies required XP.
     */
    private void checkAndAwardLevelBadges(User user) {
        List<Badge> allBadges = badgeRepository.findAll();
        Set<Long> existingBadgeIds = userBadgeRepository.findByUserId(user.getId())
                .stream()
                .map(UserBadge::getBadgeId)
                .collect(Collectors.toSet());

        for (Badge badge : allBadges) {
            if ("LEVEL".equalsIgnoreCase(badge.getCategory()) 
                    && user.getCurrentXp() >= badge.getRequiredXp() 
                    && !existingBadgeIds.contains(badge.getBadgeId())) {

                UserBadge userBadge = new UserBadge();
                userBadge.setUserId(user.getId());
                userBadge.setBadgeId(badge.getBadgeId());
                userBadge.setAwardedAt(LocalDateTime.now());
                userBadgeRepository.save(userBadge);
            }
        }
    }

    /**
     * Claws back previously-awarded XP.
     */
    @Transactional
    public User clawbackXp(Long userId, int xpAmount, String sourceType, Long sourceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (xpAmount <= 0) {
            return user;
        }

        int currentXp = (user.getCurrentXp() != null) ? user.getCurrentXp() : 0;
        int clampedXp = Math.max(0, currentXp - xpAmount);
        int actualDeducted = currentXp - clampedXp;

        if (actualDeducted > 0) {
            user.setCurrentXp(clampedXp);
            user.setUserLevel(GamificationUtils.getLevel(user));
            user.setNextLevelXpTarget(GamificationUtils.getNextLevelXpTarget(user));
            user.setUserRank(GamificationUtils.getRankTitle(user));

            XpTransaction transaction = new XpTransaction(userId, -actualDeducted, sourceType, sourceId);
            xpTransactionRepository.save(transaction);
        }

        return userRepository.save(user);
    }
}