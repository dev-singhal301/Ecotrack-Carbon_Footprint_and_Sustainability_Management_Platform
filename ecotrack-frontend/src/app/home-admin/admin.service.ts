import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminDashboardSummary } from './dashboard/dashboard.model';
import { AdminUser } from './users/users.model';

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

  // GET /api/admin/users
  getUsers(): Observable<AdminUser[]> {
    return this.http.get<AdminUser[]>(`${this.baseUrl}/users`);
  }

  // DELETE /api/admin/users/{id} — soft deactivation (User.enabled = false)
  // on the backend, not a real delete.
  deactivateUser(id: number): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/users/${id}`);
  }

  // PATCH /api/admin/users/{id}/reactivate — flips enabled back to true.
  reactivateUser(id: number): Observable<{ message: string }> {
    return this.http.patch<{ message: string }>(`${this.baseUrl}/users/${id}/reactivate`, {});
  }
}