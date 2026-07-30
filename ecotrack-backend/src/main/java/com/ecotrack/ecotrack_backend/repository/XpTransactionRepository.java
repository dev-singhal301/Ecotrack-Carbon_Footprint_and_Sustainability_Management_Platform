package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.XpTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface XpTransactionRepository extends JpaRepository<XpTransaction, Long> {

    /**
     * Aggregates monthly XP per user for the active month using PostgreSQL date_trunc.
     * Returns arrays containing [userId, name, userRank, userLevel, monthlyXp, denseRank]
     */
    @Query(value = """
        SELECT 
            u.id AS userId,
            u.name AS name,
            u.user_rank AS userRank,
            u.user_level AS userLevel,
            CAST(COALESCE(SUM(x.xp_amount), 0) AS BIGINT) AS monthlyXp,
            CAST(DENSE_RANK() OVER (ORDER BY COALESCE(SUM(x.xp_amount), 0) DESC) AS BIGINT) AS rank
        FROM users u
        LEFT JOIN xp_transactions x 
            ON u.id = x.user_id 
            AND x.created_at >= DATE_TRUNC('month', CURRENT_TIMESTAMP)
        WHERE u.enabled = TRUE
        GROUP BY u.id, u.name, u.user_rank, u.user_level
        ORDER BY rank ASC, u.name ASC
        LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findMonthlyLeaderboardRaw(@Param("limit") int limit);

    /**
     * Calculates the active user's monthly XP for the current calendar month.
     */
    @Query(value = """
        SELECT CAST(COALESCE(SUM(xp_amount), 0) AS BIGINT)
        FROM xp_transactions 
        WHERE user_id = :userId 
          AND created_at >= DATE_TRUNC('month', CURRENT_TIMESTAMP)
    """, nativeQuery = true)
    Long findCurrentMonthXpByUserId(@Param("userId") Long userId);
}