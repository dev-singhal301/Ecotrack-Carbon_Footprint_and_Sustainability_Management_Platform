import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminDashboardSummary } from './dashboard/dashboard.model';

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private readonly baseUrl = 'http://localhost:8080/api/admin';

  constructor(private http: HttpClient) {}

  // GET /api/admin/dashboard-summary
  // Auth token is attached automatically by jwtInterceptor; the backend
  // rejects this with 403 for any non-ROLE_ADMIN caller.
  getDashboardSummary(): Observable<AdminDashboardSummary> {
    return this.http.get<AdminDashboardSummary>(`${this.baseUrl}/dashboard-summary`);
  }
}