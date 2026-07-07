package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for the 'users' table.
 *
 * Extending JpaRepository<User, Long> automatically provides standard
 * CRUD operations, including:
 * - save()
 * - findAll()
 * - findById()
 * - deleteById()
 *
 * The custom methods below directly support the authentication module
 * (traditional login, registration, and Google OAuth2 login).
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Traditional Login: Looks up a user by their unique email address.
     * Used by AuthService during login and by Spring Security's
     * UserDetailsService implementation.
     */
    Optional<User> findByEmail(String email);

    /**
     * Registration Guard: Checks whether an email is already registered,
     * without loading the full User entity. Used to reject duplicate
     * sign-ups early, before attempting an insert that would fail on
     * the database's UNIQUE constraint.
     */
    boolean existsByEmail(String email);

    /**
     * Google OAuth2 Login: Looks up a user by their OAuth subject ID.
     * The 'oauth_id' column is independently UNIQUE in the schema, so
     * a single-field lookup is sufficient and avoids requiring callers
     * to also pass the provider name on every lookup.
     */
    Optional<User> findByOauthId(String oauthId);
}