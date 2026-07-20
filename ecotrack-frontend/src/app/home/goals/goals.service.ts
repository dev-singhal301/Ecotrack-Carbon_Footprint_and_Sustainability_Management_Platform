import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface GoalRequest {
  goalName: string;
  goalType: string;
  targetValue: number;
  durationDays: number;
  rewardPoints?: number;
  baselinePeriodDays?: number;
}

export interface GoalResponse {
  goalId: number;
  goalName: string;
  goalType: string;
  targetValue: number;
  baselineValue: number;
  rewardPoints: number;
  status: 'ACTIVE' | 'COMPLETED' | 'EXPIRED';
  startDate: string;
  endDate: string;
}

export interface GoalProgressResponse {
  goalId: number;
  currentValue: number;
  targetValue: number;
  baselineValue: number;
  completionPercentage: number;
  completed: boolean;
  lastUpdated?: string;
}

@Injectable({
  providedIn: 'root'
})
export class GoalService {
  private apiUrl = 'http://localhost:8080/api/goals';

  constructor(private http: HttpClient) {}

  // POST /api/goals
  createGoal(request: GoalRequest): Observable<GoalResponse> {
    return this.http.post<GoalResponse>(this.apiUrl, request);
  }

  // GET /api/goals?status=...
  getGoals(status?: string): Observable<GoalResponse[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<GoalResponse[]>(this.apiUrl, { params });
  }

  // GET /api/goals/{goalId}
  getGoalById(goalId: number): Observable<GoalResponse> {
    return this.http.get<GoalResponse>(`${this.apiUrl}/${goalId}`);
  }

  // PUT /api/goals/{goalId}
  updateGoal(goalId: number, request: GoalRequest): Observable<GoalResponse> {
    return this.http.put<GoalResponse>(`${this.apiUrl}/${goalId}`, request);
  }

  // DELETE /api/goals/{goalId}
  deleteGoal(goalId: number): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.apiUrl}/${goalId}`);
  }

  // GET /api/goals/{goalId}/progress
  getGoalProgress(goalId: number): Observable<GoalProgressResponse> {
    return this.http.get<GoalProgressResponse>(`${this.apiUrl}/${goalId}/progress`);
  }
}