package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.GoalRequest;
import com.ecotrack.ecotrack_backend.dto.GoalResponse;
import com.ecotrack.ecotrack_backend.dto.GoalProgressResponse;
import com.ecotrack.ecotrack_backend.entity.Goal;
import com.ecotrack.ecotrack_backend.entity.GoalProgress;
import com.ecotrack.ecotrack_backend.entity.GoalType;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.GoalRepository;
import com.ecotrack.ecotrack_backend.repository.GoalProgressRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalProgressRepository goalProgressRepository;
    private final UserRepository userRepository;

    public GoalService(GoalRepository goalRepository,
                        GoalProgressRepository goalProgressRepository,
                        UserRepository userRepository) {
        this.goalRepository = goalRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.userRepository = userRepository;
    }

    // ── Create ──
    public GoalResponse createGoal(Long userId, GoalRequest request) {
        validateDateOrder(request);
        GoalType parsedType = parseGoalType(request.getGoalType());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Goal goal = new Goal();
        goal.setUser(user);
        goal.setGoalName(request.getGoalName());
        goal.setGoalType(parsedType.name());
        goal.setTargetValue(request.getTargetValue());
        goal.setStartDate(request.getStartDate());
        goal.setEndDate(request.getEndDate());
        goal.setRewardPoints(request.getRewardPoints() != null ? request.getRewardPoints() : 0);
        goal.setStatus("ACTIVE");

        Goal saved = goalRepository.save(goal);

        // create an initial zeroed progress row so /progress never 404s right after creation
        GoalProgress progress = new GoalProgress();
        progress.setGoal(saved);
        progress.setCurrentValue(BigDecimal.ZERO);
        progress.setCompletionPercentage(BigDecimal.ZERO);
        progress.setCompleted(false);
        goalProgressRepository.save(progress);

        return mapToResponse(saved);
    }

    // ── Update ──
    public GoalResponse updateGoal(Long goalId, GoalRequest request) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        validateDateOrder(request);
        GoalType parsedType = parseGoalType(request.getGoalType());

        goal.setGoalName(request.getGoalName());
        goal.setGoalType(parsedType.name());
        goal.setTargetValue(request.getTargetValue());
        goal.setStartDate(request.getStartDate());
        goal.setEndDate(request.getEndDate());
        if (request.getRewardPoints() != null) {
            goal.setRewardPoints(request.getRewardPoints());
        }

        Goal saved = goalRepository.save(goal);
        return mapToResponse(saved);
    }

    // ── Read ──
    public List<GoalResponse> getGoalsForUser(Long userId) {
        return goalRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<GoalResponse> getGoalsForUserByStatus(Long userId, String status) {
        return goalRepository.findByUserIdAndStatus(userId, status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public GoalResponse getGoalById(Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        return mapToResponse(goal);
    }

    // ── Progress ──
    // NOTE: this is a placeholder until A's CarbonEntryRepository is confirmed.
    // Real implementation needs to sum matching CarbonEntry rows for this goal's
    // category/date range and compare against targetValue. Wiring TBD once
    // A shares CarbonEntryRepository's exact method signatures.
    public GoalProgressResponse getProgress(Long goalId) {
        GoalProgress progress = goalProgressRepository.findByGoalGoalId(goalId)
                .orElseThrow(() -> new IllegalArgumentException("No progress record for goal: " + goalId));

        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        return new GoalProgressResponse(
                goalId,
                progress.getCurrentValue(),
                goal.getTargetValue(),
                progress.getCompletionPercentage(),
                progress.isCompleted(),
                progress.getLastUpdated()
        );
    }

    // ── Validation helpers ──
    private void validateDateOrder(GoalRequest request) {
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }
    }

    private GoalType parseGoalType(String rawType) {
        try {
            return GoalType.valueOf(rawType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid goalType: " + rawType);
        }
    }

    // ── Mapping ──
    private GoalResponse mapToResponse(Goal goal) {
        return new GoalResponse(
                goal.getGoalId(),
                goal.getGoalName(),
                goal.getGoalType(),
                goal.getTargetValue(),
                goal.getRewardPoints(),
                goal.getStatus(),
                goal.getStartDate(),
                goal.getEndDate()
        );
    }
}