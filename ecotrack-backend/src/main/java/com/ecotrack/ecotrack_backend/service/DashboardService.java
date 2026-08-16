package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.CategoryImpactResponse;
import com.ecotrack.ecotrack_backend.dto.DashboardSummaryResponse;
import com.ecotrack.ecotrack_backend.dto.GoalProgressResponse;
import com.ecotrack.ecotrack_backend.dto.GoalSnapshotResponse;
import com.ecotrack.ecotrack_backend.dto.UserChallengeResponse;
import com.ecotrack.ecotrack_backend.dto.XpActivityResponse;
import com.ecotrack.ecotrack_backend.entity.Goal;
import com.ecotrack.ecotrack_backend.entity.GoalProgress;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.entity.XpTransaction;
import com.ecotrack.ecotrack_backend.repository.GoalProgressRepository;
import com.ecotrack.ecotrack_backend.repository.GoalRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.repository.XpTransactionRepository;
import com.ecotrack.ecotrack_backend.util.GamificationUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserChallengeService userChallengeService;
    private final XpTransactionRepository xpTransactionRepository;

    public DashboardService(UserRepository userRepository,
                            CarbonFootprintService carbonFootprintService,
                            GoalRepository goalRepository,
                            GoalProgressRepository goalProgressRepository,
                            GoalService goalService,
                            UserChallengeService userChallengeService,
                            XpTransactionRepository xpTransactionRepository) {
        this.userRepository = userRepository;
        this.carbonFootprintService = carbonFootprintService;
        this.goalRepository = goalRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.goalService = goalService;
        this.userChallengeService = userChallengeService;
        this.xpTransactionRepository = xpTransactionRepository;
    }

    /**
     * Builds and returns the unified DashboardSummaryResponse DTO.
     */
    @Transactional
    public DashboardSummaryResponse getDashboardSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        DashboardSummaryResponse summary = new DashboardSummaryResponse();

        // ── Section 1: Header & Welcome Banner ──
        summary.setUsername(user.getName());
        summary.setUserLevel(GamificationUtils.getLevel(user));
        summary.setUserRank(GamificationUtils.getRankTitle(user));
        summary.setCurrentXp(user.getCurrentXp() != null ? user.getCurrentXp() : 0);
        summary.setNextLevelXpTarget(GamificationUtils.getNextLevelXpTarget(user));

        summary.setCurrentLevelXpProgress(GamificationUtils.getCurrentLevelXpProgress(user));
        summary.setXpForNextLevel(GamificationUtils.getXpForNextLevel(user));
        
        // ── Section 2: Core KPI Metric Cards ──
        summary.setTotalCarbonFootprint(carbonFootprintService.getTotalCarbonFootprint(userId));
        summary.setCarbonSaved(carbonFootprintService.getCarbonSavedVsBaseline(userId));
        summary.setSavingsPercentage(carbonFootprintService.getSavingsPercentage(userId));
        summary.setTotalRewardPoints(user.getRewardPoints() != null ? user.getRewardPoints() : 0);

        // ── Leaderboard Standing (Replacement for Eco-Budget Health) ──
        Long monthlyXp = xpTransactionRepository.findCurrentMonthXpByUserId(userId);
        Long monthlyRank = xpTransactionRepository.findCurrentMonthRankByUserId(userId);
        summary.setMonthlyXp(monthlyXp != null ? monthlyXp : 0L);
        summary.setMonthlyRank(monthlyRank != null ? monthlyRank : 0L);

        // ── Section 3: Category Emission Impact ──
        List<CategoryImpactResponse> categoryImpacts = carbonFootprintService.getCategoryImpactList(userId);
        summary.setCategoryImpacts(categoryImpacts);

        // ── Section 4: Recent XP Earned Feed ──
        List<XpTransaction> recentTransactions = xpTransactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 15));

        List<XpActivityResponse> recentActivities = recentTransactions.stream()
                .map(tx -> new XpActivityResponse(
                        tx.getXpTransactionId(),
                        tx.getSourceType(),
                        formatXpDescription(tx.getSourceType()),
                        tx.getXpAmount(),
                        tx.getCreatedAt()
                ))
                .toList();
        summary.setRecentActivities(recentActivities);

        // ── Section 5: Active Goal Meters ──
        List<GoalSnapshotResponse> activeGoalSnapshots = getActiveGoalSnapshots(userId);
        summary.setActiveGoals(activeGoalSnapshots);

        // ── Section 6: Challenge Snapshots & Metrics ──
        List<UserChallengeResponse> allUserChallenges = userChallengeService.getUserChallenges(user.getEmail());

        List<UserChallengeResponse> activeChallenges = allUserChallenges.stream()
                .filter(c -> "IN_PROGRESS".equalsIgnoreCase(c.getUserStatus()))
                .toList();

        long completedCount = allUserChallenges.stream()
                .filter(c -> "COMPLETED".equalsIgnoreCase(c.getUserStatus()))
                .count();

        summary.setActiveChallengesCount(activeChallenges.size());
        summary.setCompletedChallengesCount((int) completedCount);
        summary.setActiveChallenges(activeChallenges);

        return summary;
    }

    /**
     * Converts a raw sourceType into a user-friendly UI description.
     */
    private String formatXpDescription(String sourceType) {
        if (sourceType == null) {
            return "XP Reward Earned";
        }
        return switch (sourceType.toUpperCase()) {
            case "GOAL" -> "Goal Completed Reward";
            case "CHALLENGE" -> "Challenge Accomplished";
            case "CHALLENGE_CANCELLED" -> "Challenge Cancelled — XP Deducted";
            case "DAILY_LOG", "LOG" -> "Daily Log Entry Reward";
            case "STREAK" -> "Streak Milestone Reward";
            default -> "Activity XP Awarded";
        };
    }

    /**
     * Helper to fetch active goals and construct GoalSnapshotResponse items for mini meters.
     */
    private List<GoalSnapshotResponse> getActiveGoalSnapshots(Long userId) {
        List<Goal> activeGoals = goalRepository.findByUserIdAndStatus(userId, "ACTIVE");
        List<GoalSnapshotResponse> snapshots = new ArrayList<>();

        for (Goal goal : activeGoals) {
            GoalProgressResponse progressResp = null;

            try {
                progressResp = goalService.calculateProgress(userId, goal.getGoalId());
            } catch (Exception ignored) {
                // Fallback handling if live calculation fails transiently
            }

            Goal updatedGoal = goalRepository.findById(goal.getGoalId()).orElse(goal);
            if (!"ACTIVE".equalsIgnoreCase(updatedGoal.getStatus())) {
                continue;
            }

            BigDecimal currentValue = BigDecimal.ZERO;
            BigDecimal completionPercentage = BigDecimal.ZERO;

            if (progressResp != null) {
                currentValue = progressResp.getCurrentValue() != null ? progressResp.getCurrentValue() : BigDecimal.ZERO;
                completionPercentage = progressResp.getCompletionPercentage() != null ? progressResp.getCompletionPercentage() : BigDecimal.ZERO;
            } else {
                GoalProgress progress = goalProgressRepository.findByGoalGoalId(goal.getGoalId()).orElse(null);
                if (progress != null) {
                    currentValue = progress.getCurrentValue() != null ? progress.getCurrentValue() : BigDecimal.ZERO;
                    completionPercentage = progress.getCompletionPercentage() != null ? progress.getCompletionPercentage() : BigDecimal.ZERO;
                }
            }

            snapshots.add(new GoalSnapshotResponse(
                    updatedGoal.getGoalId(),
                    updatedGoal.getGoalName(),
                    updatedGoal.getGoalType(),
                    updatedGoal.getTargetValue(),
                    currentValue,
                    completionPercentage,
                    updatedGoal.getRewardPoints()
            ));
        }

        return snapshots;
    }
}