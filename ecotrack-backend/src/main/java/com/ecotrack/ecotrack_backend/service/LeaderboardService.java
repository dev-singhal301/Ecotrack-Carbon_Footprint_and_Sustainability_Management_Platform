package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.LeaderboardUserDto;
import com.ecotrack.ecotrack_backend.dto.UserPastHistoryDto;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.repository.XpTransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class LeaderboardService {

    private final XpTransactionRepository xpTransactionRepository;
    private final UserRepository userRepository;
    private final UserXpService userXpService;

    public LeaderboardService(XpTransactionRepository xpTransactionRepository,
                              UserRepository userRepository,
                              UserXpService userXpService) {
        this.xpTransactionRepository = xpTransactionRepository;
        this.userRepository = userRepository;
        this.userXpService = userXpService;
    }

    /**
     * Gets top N leaderboard users based on all-time total XP.
     */
    @Transactional(readOnly = true)
    public List<LeaderboardUserDto> getAllTimeLeaderboard(int limit) {
        List<User> topUsers = userRepository.findAllByOrderByCurrentXpDesc(PageRequest.of(0, limit));
        List<LeaderboardUserDto> leaderboard = new ArrayList<>();

        long currentRank = 1;
        for (User u : topUsers) {
            String name = (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getEmail();
            leaderboard.add(new LeaderboardUserDto(
                    u.getId(),
                    name,
                    u.getUserRank(),
                    u.getUserLevel(),
                    (long) u.getCurrentXp(),
                    currentRank++
            ));
        }

        return leaderboard;
    }

    /**
     * Gets top N leaderboard users for the current month.
     */
    @Transactional(readOnly = true)
    public List<LeaderboardUserDto> getMonthlyLeaderboard(int limit) {
        List<Object[]> rawResults = xpTransactionRepository.findMonthlyLeaderboardRaw(limit);
        List<LeaderboardUserDto> leaderboard = new ArrayList<>();

        for (Object[] row : rawResults) {
            Long userId = ((Number) row[0]).longValue();
            String name = (String) row[1];
            String userRank = (String) row[2];
            Integer userLevel = ((Number) row[3]).intValue();
            Long monthlyXp = ((Number) row[4]).longValue();
            Long rank = ((Number) row[5]).longValue();

            leaderboard.add(new LeaderboardUserDto(userId, name, userRank, userLevel, monthlyXp, rank));
        }

        return leaderboard;
    }

    /**
     * Gets top N leaderboard users for the previous calendar month.
     * Used for "Last Month's Champions" card.
     */
    @Transactional(readOnly = true)
    public List<LeaderboardUserDto> getPreviousMonthLeaderboard(int limit) {
        List<Object[]> rawResults = xpTransactionRepository.findPreviousMonthLeaderboardRaw(limit);
        List<LeaderboardUserDto> leaderboard = new ArrayList<>();

        for (Object[] row : rawResults) {
            Long userId = ((Number) row[0]).longValue();
            String name = (String) row[1];
            String userRank = (String) row[2];
            Integer userLevel = ((Number) row[3]).intValue();
            Long monthlyXp = ((Number) row[4]).longValue();
            Long rank = ((Number) row[5]).longValue();

            leaderboard.add(new LeaderboardUserDto(userId, name, userRank, userLevel, monthlyXp, rank));
        }

        return leaderboard;
    }

    /**
     * Gets past monthly performance history for a specific user.
     * Used for "Your Previous Ranks / History" card.
     */
    @Transactional(readOnly = true)
    public List<UserPastHistoryDto> getUserPastHistory(Long userId, int limit) {
        List<Object[]> rawResults = xpTransactionRepository.findUserPastMonthlyHistory(userId, limit);
        List<UserPastHistoryDto> history = new ArrayList<>();

        for (Object[] row : rawResults) {
            String monthKey = (String) row[0];
            Long totalXp = ((Number) row[1]).longValue();
            history.add(new UserPastHistoryDto(monthKey, totalXp));
        }

        return history;
    }

    /**
     * Gets the overall standing/rank position of a specific user.
     */
    @Transactional(readOnly = true)
    public LeaderboardUserDto getUserRankPosition(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        long rankPosition = userRepository.countByCurrentXpGreaterThan(user.getCurrentXp()) + 1;
        String name = (user.getName() != null && !user.getName().isBlank()) ? user.getName() : user.getEmail();

        return new LeaderboardUserDto(
                user.getId(),
                name,
                user.getUserRank(),
                user.getUserLevel(),
                (long) user.getCurrentXp(),
                rankPosition
        );
    }

    /**
     * Delegated wrapper for awarding XP to maintain backward compatibility with any legacy callers.
     */
    @Transactional
    public void awardXp(Long userId, Integer xpAmount, String sourceType, Long sourceId) {
        userXpService.awardXpAndPoints(userId, xpAmount, 0, sourceType, sourceId);
    }

    /**
     * Claws back previously-awarded XP and records the matching negative
     * XpTransaction, so monthly/previous-month leaderboards and XP history
     * (which sum xp_transactions) reconcile with the all-time total on
     * User.currentXp. See UserXpService.clawbackXp for the full contract.
     */
    @Transactional
    public void clawbackXp(Long userId, Integer xpAmount, String sourceType, Long sourceId) {
        userXpService.clawbackXp(userId, xpAmount, sourceType, sourceId);
    }
}