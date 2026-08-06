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

    /**
     * Claws back previously-awarded XP (e.g. an admin cancelling a challenge
     * a user had already completed). Mirrors awardXpAndPoints's contract —
     * updates User.currentXp AND writes the matching XpTransaction row —
     * but the amount is deducted rather than added, and clamped at 0 rather
     * than allowed to go negative on the running total.
     *
     * The actual amount deducted (which may be less than xpAmount if the
     * clamp kicks in) is what gets written to the ledger, so
     * SUM(xp_amount) over xp_transactions always reconciles with
     * User.currentXp — without this, cascadeCancellationToParticipants was
     * decrementing currentXp directly and skipping the ledger entirely,
     * so monthly/previous-month leaderboards and XP history (which sum
     * xp_transactions by date range, not currentXp) never reflected the
     * clawback.
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