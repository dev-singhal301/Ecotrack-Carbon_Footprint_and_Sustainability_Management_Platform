package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Long> {
    Optional<Badge> findByName(String name);
    List<Badge> findByCategory(String category);
}