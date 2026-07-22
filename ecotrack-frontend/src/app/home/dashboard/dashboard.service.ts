import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DashboardSummary } from './dashboard.model';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private readonly baseUrl = 'http://localhost:8080/api/dashboard';

  constructor(private http: HttpClient) {}

  // GET /api/dashboard/summary
  // Also triggers logDailySnapshot(userId) server-side as a side effect
  // (see DashboardController) — nothing the frontend needs to do about
  // that, just noting it's not a pure read.
  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.baseUrl}/summary`);
  }
}