package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.LeaderboardUserDto;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.entity.XpTransaction;
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

    public LeaderboardService(XpTransactionRepository xpTransactionRepository,
                              UserRepository userRepository) {
        this.xpTransactionRepository = xpTransactionRepository;
        this.userRepository = userRepository;
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
     * Gets the overall standing/rank position of a specific user.
     */
    @Transactional(readOnly = true)
    public LeaderboardUserDto getUserRankPosition(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        // Rank position is 1 + number of users who have strictly more XP
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
     * Universal helper method to record XP when a user completes a goal or challenge.
     */
    @Transactional
    public void awardXp(Long userId, Integer xpAmount, String sourceType, Long sourceId) {
        if (xpAmount == null || xpAmount <= 0) return;

        XpTransaction transaction = new XpTransaction(userId, xpAmount, sourceType, sourceId);
        xpTransactionRepository.save(transaction);
    }
}