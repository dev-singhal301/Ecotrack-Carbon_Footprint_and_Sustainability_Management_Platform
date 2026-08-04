package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.entity.XpTransaction;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.repository.XpTransactionRepository;
import com.ecotrack.ecotrack_backend.util.GamificationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserXpService {

    private final UserRepository userRepository;
    private final XpTransactionRepository xpTransactionRepository;

    public UserXpService(UserRepository userRepository, XpTransactionRepository xpTransactionRepository) {
        this.userRepository = userRepository;
        this.xpTransactionRepository = xpTransactionRepository;
    }

    /**
     * Centralized XP Engine Method:
     * 1. Accumulates permanent XP & reward points on User entity.
     * 2. Syncs level, target XP, and rank title using GamificationUtils.
     * 3. Creates an XpTransaction record to feed the monthly leaderboard.
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
        }

        if (rewardPoints > 0) {
            int currentPoints = (user.getRewardPoints() != null) ? user.getRewardPoints() : 0;
            user.setRewardPoints(currentPoints + rewardPoints);
        }

        return userRepository.save(user);
    }
}