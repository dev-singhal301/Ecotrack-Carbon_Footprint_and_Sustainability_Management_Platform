package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.LeaderboardUserDto;
import com.ecotrack.ecotrack_backend.entity.XpTransaction;
import com.ecotrack.ecotrack_backend.repository.XpTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class LeaderboardService {

    @Autowired
    private XpTransactionRepository xpTransactionRepository;

    /**
     * Gets top N leaderboard users for the current month.
     */
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
     * Universal helper method to record XP when a user completes a goal or challenge.
     */
    @Transactional
    public void awardXp(Long userId, Integer xpAmount, String sourceType, Long sourceId) {
        if (xpAmount == null || xpAmount <= 0) return;
        
        XpTransaction transaction = new XpTransaction(userId, xpAmount, sourceType, sourceId);
        xpTransactionRepository.save(transaction);
    }
}