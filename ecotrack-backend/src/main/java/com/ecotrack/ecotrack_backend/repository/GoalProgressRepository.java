package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.GoalProgress;
import com.ecotrack.ecotrack_backend.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoalProgressRepository extends JpaRepository<GoalProgress, Long> {

    Optional<GoalProgress> findByGoalGoalId(Long goalId);

    Optional<GoalProgress> findByGoal(Goal goal);

    void deleteByGoalGoalId(Long goalId);
}