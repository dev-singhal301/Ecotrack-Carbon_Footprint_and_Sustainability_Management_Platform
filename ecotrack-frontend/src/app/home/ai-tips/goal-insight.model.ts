export type GoalInsightSource = 'AI' | 'STANDARD';
export type GoalInsightStatusStr = 'ON_TRACK' | 'AT_RISK' | 'OVER_BUDGET' | 'FAILED' | 'COMPLETED';

export interface GoalInsightDetail {
  status: GoalInsightStatusStr;
  message: string;
  action: string;
  source: GoalInsightSource;
}

export interface GoalInsightResponse {
  goalId: number;
  goalName: string;
  goalType: string;
  currentValue: number;
  targetValue: number;
  completionPercentage: number;
  completed: boolean;
  endDate: string;
  insight: GoalInsightDetail;
}

export interface GoalInsightsBatchResponse {
  insights: GoalInsightResponse[];
}
