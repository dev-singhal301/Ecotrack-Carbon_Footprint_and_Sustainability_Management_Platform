import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GoalResponse, GoalProgressResponse } from './goal.model';

@Injectable({
  providedIn: 'root'
})
export class GoalService {
  private readonly baseUrl = 'http://localhost:8080/api/goals';

  constructor(private http: HttpClient) {}

  // GET /api/goals?status=ACTIVE
  // Confirmed: returns 200 OK with [] when no goals exist — never null/404.
  getActiveGoals(): Observable<GoalResponse[]> {
    return this.http.get<GoalResponse[]>(`${this.baseUrl}?status=ACTIVE`);
  }

  // GET /api/goals — no status filter, returns every goal regardless of status
  getAllGoals(): Observable<GoalResponse[]> {
    return this.http.get<GoalResponse[]>(this.baseUrl);
  }

  // GET /api/goals/{id}/progress
  getGoalProgress(goalId: number): Observable<GoalProgressResponse> {
    return this.http.get<GoalProgressResponse>(`${this.baseUrl}/${goalId}/progress`);
  }
}
