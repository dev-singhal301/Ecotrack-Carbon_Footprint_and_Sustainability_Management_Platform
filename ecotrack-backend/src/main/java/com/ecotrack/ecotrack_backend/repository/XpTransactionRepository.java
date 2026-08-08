package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.XpTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface XpTransactionRepository extends JpaRepository<XpTransaction, Long> {

    /**
     * Fetches itemized XP transaction history for a given user ordered by most recent.
     */
    List<XpTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Fetches top N recent XP transactions for a user (used for Dashboard activity list).
     * Usage: findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 15));
     */
    List<XpTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

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

    /**
     * Calculates a specific user's rank position for the active calendar month.
     */
    @Query(value = """
        WITH monthly_totals AS (
            SELECT 
                u.id AS user_id, 
                COALESCE(SUM(x.xp_amount), 0) AS total_xp
            FROM users u
            LEFT JOIN xp_transactions x 
                ON u.id = x.user_id 
                AND x.created_at >= DATE_TRUNC('month', CURRENT_TIMESTAMP)
            WHERE u.enabled = TRUE
            GROUP BY u.id
        ),
        ranked_totals AS (
            SELECT 
                user_id, 
                CAST(DENSE_RANK() OVER (ORDER BY total_xp DESC) AS BIGINT) AS rank
            FROM monthly_totals
        )
        SELECT rank FROM ranked_totals WHERE user_id = :userId
    """, nativeQuery = true)
    Long findCurrentMonthRankByUserId(@Param("userId") Long userId);

    /**
     * Aggregates monthly XP per user for the previous calendar month.
     * Used for "Last Month's Champions" (top 3) or previous month podiums.
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
        INNER JOIN xp_transactions x 
            ON u.id = x.user_id 
            AND x.created_at >= DATE_TRUNC('month', CURRENT_TIMESTAMP - INTERVAL '1 month')
            AND x.created_at < DATE_TRUNC('month', CURRENT_TIMESTAMP)
        WHERE u.enabled = TRUE
        GROUP BY u.id, u.name, u.user_rank, u.user_level
        ORDER BY rank ASC, u.name ASC
        LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findPreviousMonthLeaderboardRaw(@Param("limit") int limit);

    /**
     * Fetches a single user's aggregated XP per past month (e.g., [ "2026-07", 420 ]).
     * Used for "Your Previous Ranks / History" card.
     */
    @Query(value = """
        SELECT 
            TO_CHAR(x.created_at, 'YYYY-MM') AS monthKey,
            CAST(SUM(x.xp_amount) AS BIGINT) AS totalXp
        FROM xp_transactions x
        WHERE x.user_id = :userId
          AND x.created_at < DATE_TRUNC('month', CURRENT_TIMESTAMP)
        GROUP BY TO_CHAR(x.created_at, 'YYYY-MM')
        ORDER BY monthKey DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findUserPastMonthlyHistory(@Param("userId") Long userId, @Param("limit") int limit);
}