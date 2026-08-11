import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminDashboardSummary, AdminParticipation } from './dashboard/dashboard.model';
import { AdminUser } from './users/users.model';
import { Challenge } from '../../types/challenge';
import { ChallengeAdminRequest } from './challenges/challenges.model';

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

  // GET /api/admin/challenges
  getChallenges(): Observable<Challenge[]> {
    return this.http.get<Challenge[]>(`${this.baseUrl}/challenges`);
  }

  // POST /api/admin/challenges
  createChallenge(payload: ChallengeAdminRequest): Observable<Challenge> {
    return this.http.post<Challenge>(`${this.baseUrl}/challenges`, payload);
  }

  // PUT /api/admin/challenges/{id} — rejected with 409 once the challenge
  // has started (edit lock — see ChallengeService.updateChallenge).
  updateChallenge(id: number, payload: ChallengeAdminRequest): Observable<Challenge> {
    return this.http.put<Challenge>(`${this.baseUrl}/challenges/${id}`, payload);
  }

  // DELETE /api/admin/challenges/{id} — soft cancel, not a real delete.
  // Rejected with 409 if already cancelled or already ended.
  cancelChallenge(id: number): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/challenges/${id}`);
  }

  // GET /api/admin/participations — every participation across every user,
  // for dashboard analytics (current in-progress count, activity trend,
  // resolved completion rate trend). Fetched once; filtered/bucketed by
  // date range client-side, same pattern as getChallenges()/getUsers().
  getParticipations(): Observable<AdminParticipation[]> {
    return this.http.get<AdminParticipation[]>(`${this.baseUrl}/participations`);
  }
}