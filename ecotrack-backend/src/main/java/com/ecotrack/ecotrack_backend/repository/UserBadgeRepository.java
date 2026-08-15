package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.dto.UserBadgeResponse;
import com.ecotrack.ecotrack_backend.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    List<UserBadge> findByUserId(Long userId);

    boolean existsByUserIdAndBadgeId(Long userId, Long badgeId);

    Optional<UserBadge> findByUserIdAndBadgeId(Long userId, Long badgeId);

    long countByUserId(Long userId);

    @Query("SELECT new com.ecotrack.ecotrack_backend.dto.UserBadgeResponse(" +
           "b.badgeId, b.name, b.description, b.category, b.iconUrl, b.requiredXp, true, ub.awardedAt) " +
           "FROM UserBadge ub JOIN Badge b ON ub.badgeId = b.badgeId " +
           "WHERE ub.userId = :userId " +
           "ORDER BY ub.awardedAt DESC")
    List<UserBadgeResponse> findBadgesByUserId(@Param("userId") Long userId);
}