package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;
import java.util.List;

public class DashboardSummaryResponse {

    // --- Section 1: Header & Welcome Banner ---
    private String username;
    private Integer userLevel;               // e.g., Level 1
    private String userRank;                // e.g., "Novice Eco"
    private Integer currentXp;
    private Integer nextLevelXpTarget;

    // --- Section 2: Core KPI Metric Cards ---
    private BigDecimal totalCarbonFootprint; // kg CO2e
    private BigDecimal carbonSaved;          // kg CO2e saved vs baseline
    private Double savingsPercentage;        // Percentage drop badge (e.g., 12.5%)
    private Integer totalRewardPoints;
    
    // --- Leaderboard Standing (New Replacement for Eco-Budget Health) ---
    private Long monthlyRank;                // e.g., 3 (#3 in active month)
    private Long monthlyXp;                  // e.g., 450 XP earned this month

    // --- Section 3: Category Emission Impact ---
    private List<CategoryImpactResponse> categoryImpacts;

    // --- Section 4: Recent XP Earned Feed ---
    private List<XpActivityResponse> recentActivities;

    // --- Section 5: Active Goal Meters ---
    private List<GoalSnapshotResponse> activeGoals;

    // --- Section 6: Challenge Snapshots & Metrics ---
    private Integer activeChallengesCount;
    private Integer completedChallengesCount;
    private List<UserChallengeResponse> activeChallenges;

    // Constructors
    public DashboardSummaryResponse() {}

    public DashboardSummaryResponse(String username, Integer userLevel, String userRank, Integer currentXp, Integer nextLevelXpTarget,
                                    BigDecimal totalCarbonFootprint, BigDecimal carbonSaved, Double savingsPercentage,
                                    Integer totalRewardPoints, Long monthlyRank, Long monthlyXp,
                                    List<CategoryImpactResponse> categoryImpacts, List<XpActivityResponse> recentActivities,
                                    List<GoalSnapshotResponse> activeGoals, Integer activeChallengesCount,
                                    Integer completedChallengesCount, List<UserChallengeResponse> activeChallenges) {
        this.username = username;
        this.userLevel = userLevel;
        this.userRank = userRank;
        this.currentXp = currentXp;
        this.nextLevelXpTarget = nextLevelXpTarget;
        this.totalCarbonFootprint = totalCarbonFootprint;
        this.carbonSaved = carbonSaved;
        this.savingsPercentage = savingsPercentage;
        this.totalRewardPoints = totalRewardPoints;
        this.monthlyRank = monthlyRank;
        this.monthlyXp = monthlyXp;
        this.categoryImpacts = categoryImpacts;
        this.recentActivities = recentActivities;
        this.activeGoals = activeGoals;
        this.activeChallengesCount = activeChallengesCount;
        this.completedChallengesCount = completedChallengesCount;
        this.activeChallenges = activeChallenges;
    }

    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Integer getUserLevel() { return userLevel; }
    public void setUserLevel(Integer userLevel) { this.userLevel = userLevel; }

    public String getUserRank() { return userRank; }
    public void setUserRank(String userRank) { this.userRank = userRank; }

    public Integer getCurrentXp() { return currentXp; }
    public void setCurrentXp(Integer currentXp) { this.currentXp = currentXp; }

    public Integer getNextLevelXpTarget() { return nextLevelXpTarget; }
    public void setNextLevelXpTarget(Integer nextLevelXpTarget) { this.nextLevelXpTarget = nextLevelXpTarget; }

    public BigDecimal getTotalCarbonFootprint() { return totalCarbonFootprint; }
    public void setTotalCarbonFootprint(BigDecimal totalCarbonFootprint) { this.totalCarbonFootprint = totalCarbonFootprint; }

    public BigDecimal getCarbonSaved() { return carbonSaved; }
    public void setCarbonSaved(BigDecimal carbonSaved) { this.carbonSaved = carbonSaved; }

    public Double getSavingsPercentage() { return savingsPercentage; }
    public void setSavingsPercentage(Double savingsPercentage) { this.savingsPercentage = savingsPercentage; }

    public Integer getTotalRewardPoints() { return totalRewardPoints; }
    public void setTotalRewardPoints(Integer totalRewardPoints) { this.totalRewardPoints = totalRewardPoints; }

    public Long getMonthlyRank() { return monthlyRank; }
    public void setMonthlyRank(Long monthlyRank) { this.monthlyRank = monthlyRank; }

    public Long getMonthlyXp() { return monthlyXp; }
    public void setMonthlyXp(Long monthlyXp) { this.monthlyXp = monthlyXp; }

    public List<CategoryImpactResponse> getCategoryImpacts() { return categoryImpacts; }
    public void setCategoryImpacts(List<CategoryImpactResponse> categoryImpacts) { this.categoryImpacts = categoryImpacts; }

    public List<XpActivityResponse> getRecentActivities() { return recentActivities; }
    public void setRecentActivities(List<XpActivityResponse> recentActivities) { this.recentActivities = recentActivities; }

    public List<GoalSnapshotResponse> getActiveGoals() { return activeGoals; }
    public void setActiveGoals(List<GoalSnapshotResponse> activeGoals) { this.activeGoals = activeGoals; }

    public Integer getActiveChallengesCount() { return activeChallengesCount; }
    public void setActiveChallengesCount(Integer activeChallengesCount) { this.activeChallengesCount = activeChallengesCount; }

    public Integer getCompletedChallengesCount() { return completedChallengesCount; }
    public void setCompletedChallengesCount(Integer completedChallengesCount) { this.completedChallengesCount = completedChallengesCount; }

    public List<UserChallengeResponse> getActiveChallenges() { return activeChallenges; }
    public void setActiveChallenges(List<UserChallengeResponse> activeChallenges) { this.activeChallenges = activeChallenges; }
}