package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    List<UserBadge> findByUserId(Long userId);
    boolean existsByUserIdAndBadgeId(Long userId, Long badgeId);
    Optional<UserBadge> findByUserIdAndBadgeId(Long userId, Long badgeId);
}