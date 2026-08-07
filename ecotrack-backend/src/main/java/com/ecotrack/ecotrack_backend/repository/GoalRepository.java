package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserId(Long userId);

    List<Goal> findByUserIdAndStatus(Long userId, String status);

    long countByUserIdAndStatus(Long userId, String status);

    // Used by the daily maintenance sweep to settle every goal whose
    // window has closed, regardless of which user owns it.
    List<Goal> findByStatus(String status);
}