package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for the 'roles' table.
 *
 * Extending JpaRepository<Role, Long> automatically provides standard
 * CRUD operations, including:
 * - save()
 * - findAll()
 * - findById()
 * - deleteById()
 *
 * No implementation is written manually — Spring Data JPA generates
 * the implementation at runtime based on the method name and the
 * interface's generic types (Role = entity, Long = primary key type).
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Finds a role by its unique name (e.g., "ROLE_USER", "ROLE_ADMIN").
     * Used when assigning a default role during user registration.
     */
    Optional<Role> findByName(String name);
}