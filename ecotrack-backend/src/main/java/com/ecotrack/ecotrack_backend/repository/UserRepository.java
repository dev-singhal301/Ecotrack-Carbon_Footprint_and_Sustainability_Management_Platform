package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the 'users' table.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Traditional Login: Looks up a user by their unique email address.
     */
    Optional<User> findByEmail(String email);

    /**
     * Registration Guard: Checks whether an email is already registered.
     */
    boolean existsByEmail(String email);

    /**
     * Google OAuth2 Login: Looks up a user by their OAuth subject ID.
     */
    Optional<User> findByOauthId(String oauthId);

    /**
     * Reset Password Token lookup.
     */
    Optional<User> findByResetPasswordToken(String resetPasswordToken);

    // --- Admin Dashboard summary queries ---
    // Kept as narrow, purpose-built count queries rather than pulling full
    // User lists into memory just to count them.

    long countByEnabled(boolean enabled);

    long countByRole_Name(String roleName);

    long countByCreatedAtAfter(java.time.LocalDateTime after);
    // ── Leaderboard & Gamification Additions ──

    /**
     * Fetches top users sorted by current total XP for all-time leaderboards.
     */
    List<User> findAllByOrderByCurrentXpDesc(Pageable pageable);

    /**
     * Counts how many users have strictly more XP than a given threshold,
     * used to compute a user's exact overall rank position.
     */
    long countByCurrentXpGreaterThan(Integer currentXp);
}