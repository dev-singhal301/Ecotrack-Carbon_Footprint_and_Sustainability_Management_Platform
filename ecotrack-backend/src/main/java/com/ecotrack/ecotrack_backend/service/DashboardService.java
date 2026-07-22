package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.CategoryImpactResponse;
import com.ecotrack.ecotrack_backend.dto.DashboardSummaryResponse;
import com.ecotrack.ecotrack_backend.dto.GoalSnapshotResponse;
import com.ecotrack.ecotrack_backend.entity.Goal;
import com.ecotrack.ecotrack_backend.entity.GoalProgress;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.GoalProgressRepository;
import com.ecotrack.ecotrack_backend.repository.GoalRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.util.GamificationUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final CarbonFootprintService carbonFootprintService;
    private final GoalRepository goalRepository;
    private final GoalProgressRepository goalProgressRepository;
    private final GoalService goalService;

    public DashboardService(UserRepository userRepository,
                            CarbonFootprintService carbonFootprintService,
                            GoalRepository goalRepository,
                            GoalProgressRepository goalProgressRepository,
                            GoalService goalService) {
        this.userRepository = userRepository;
        this.carbonFootprintService = carbonFootprintService;
        this.goalRepository = goalRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.goalService = goalService;
    }

    /**
     * Builds and returns the unified DashboardSummaryResponse DTO.
     */
    public DashboardSummaryResponse getDashboardSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        DashboardSummaryResponse summary = new DashboardSummaryResponse();

        // ── Section 1: Header & Welcome Banner ──
        summary.setUsername(user.getName());
        summary.setUserLevel(GamificationUtils.getLevel(user));
        summary.setUserRank(GamificationUtils.getRankTitle(user));
        summary.setCurrentXp(user.getCurrentXp());
        summary.setNextLevelXpTarget(GamificationUtils.getNextLevelXpTarget(user));

        // ── Section 2: Core KPI Metric Cards ──
        summary.setTotalCarbonFootprint(carbonFootprintService.getTotalCarbonFootprint(userId));
        summary.setCarbonSaved(carbonFootprintService.getCarbonSavedVsBaseline(userId));
        summary.setSavingsPercentage(carbonFootprintService.getSavingsPercentage(userId));
        summary.setTotalRewardPoints(user.getRewardPoints());

        // ── Section 3: Category Emission Impact ──
        List<CategoryImpactResponse> categoryImpacts = carbonFootprintService.getCategoryImpactList(userId);
        summary.setCategoryImpacts(categoryImpacts);

        // ── Section 4: Active Goal Meters ──
        List<GoalSnapshotResponse> activeGoalSnapshots = getActiveGoalSnapshots(userId);
        summary.setActiveGoals(activeGoalSnapshots);

        return summary;
    }

    /**
     * Helper to fetch active goals and construct GoalSnapshotResponse items for mini meters.
     */
    private List<GoalSnapshotResponse> getActiveGoalSnapshots(Long userId) {
        List<Goal> activeGoals = goalRepository.findByUserIdAndStatus(userId, "ACTIVE");
        List<GoalSnapshotResponse> snapshots = new ArrayList<>();

        for (Goal goal : activeGoals) {
            // Re-calculate live progress for active goal
            try {
                goalService.calculateProgress(userId, goal.getGoalId());
            } catch (Exception ignored) {
                // Keep fallback calculations if calculation triggers transient issues
            }

            GoalProgress progress = goalProgressRepository.findByGoalGoalId(goal.getGoalId())
                    .orElse(null);

            BigDecimal currentValue = progress != null && progress.getCurrentValue() != null
                    ? progress.getCurrentValue() : BigDecimal.ZERO;
            BigDecimal completionPercentage = progress != null && progress.getCompletionPercentage() != null
                    ? progress.getCompletionPercentage() : BigDecimal.ZERO;

            snapshots.add(new GoalSnapshotResponse(
                    goal.getGoalId(),
                    goal.getGoalName(),
                    goal.getGoalType(),
                    goal.getTargetValue(),
                    currentValue,
                    completionPercentage,
                    goal.getRewardPoints()
            ));
        }

        return snapshots;
    }
}