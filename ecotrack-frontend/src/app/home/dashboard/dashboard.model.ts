export interface CategoryImpact {
  categoryName: string;
  totalEmissions: number;
  percentageShare: number;
}

export interface GoalSnapshot {
  goalId: number;
  goalName: string;
  goalType: string;
  targetValue: number;
  currentValue: number;
  completionPercentage: number;
  rewardPoints: number;
}

export interface XpActivityItem {
  xpTransactionId?: number;
  id?: number;
  sourceType?: string;
  category?: string;
  description?: string;
  activityName?: string;
  xpEarned: number;
  createdAt?: string;
  timestamp?: string;
}

export interface DashboardSummary {
  username: string;
  userLevel: number;
  userRank: string;
  currentXp: number;
  nextLevelXpTarget: number;
  currentLevelXpProgress: number; 
  xpForNextLevel: number;        
  totalCarbonFootprint: number;
  carbonSaved: number;
  savingsPercentage: number;
  totalRewardPoints: number;
  monthlyXp: number;
  monthlyRank: number;
  categoryImpacts: CategoryImpact[];
  activeGoals: GoalSnapshot[];
  recentActivities?: XpActivityItem[];
}