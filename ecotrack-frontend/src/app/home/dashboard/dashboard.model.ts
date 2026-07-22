// Matches GET /api/dashboard/summary exactly, per DashboardSummaryResponse.java,
// CategoryImpactResponse.java, and GoalSnapshotResponse.java.

export interface CategoryImpact {
  categoryName: string;
  totalEmissions: number;
  percentageShare: number;
}

// No status/daysLeft field — this list is pre-filtered server-side to
// "active" goals. currentValue/targetValue still drive the fill bar and
// status pill here defensively, in case DashboardService's active-goal
// filtering ever drifts from GoalService's own isTaskActive() exclusion
// of breached goals (two separate backend implementations, real risk of
// going out of sync over time).
export interface GoalSnapshot {
  goalId: number;
  goalName: string;
  goalType: string;
  targetValue: number;
  currentValue: number;
  completionPercentage: number;
  rewardPoints: number;
}

export interface DashboardSummary {
  username: string;
  userLevel: number;
  userRank: string;
  currentXp: number;
  nextLevelXpTarget: number;
  totalCarbonFootprint: number;
  carbonSaved: number;
  savingsPercentage: number;
  totalRewardPoints: number;
  categoryImpacts: CategoryImpact[];
  activeGoals: GoalSnapshot[];
}