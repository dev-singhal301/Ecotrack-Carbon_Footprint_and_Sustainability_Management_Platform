export interface GoalResponse {
  goalId: number;
  goalName: string;
  goalType: string;
  targetValue: number;
  rewardPoints: number;
  status: string;
  startDate: string;
  endDate: string;
  baselineValue: number;
}

export interface GoalProgressResponse {
  goalId: number;
  currentValue: number;
  targetValue: number;
  completionPercentage: number;
  completed: boolean;
  lastUpdated: string;
  baselineValue: number;
}
