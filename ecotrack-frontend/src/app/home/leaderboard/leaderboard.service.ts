import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface LeaderboardUser {
  userId: number;
  name: string;
  userRank: string;
  userLevel: number;
  monthlyXp: number;
  totalXp?: number;
  xpGained?: number;
  rank: number;
}

export interface UserHistoryItem {
  monthKey: string;
  totalXp: number;
  rank?: number;
  xpGained?: number;
}

@Injectable({
  providedIn: 'root'
})
export class LeaderboardService {
  private readonly baseUrl = `${environment.apiUrl || 'http://localhost:8080'}/api/leaderboard`;

  constructor(private http: HttpClient) {}

  /** Fetch current monthly season standings */
  getMonthlyLeaderboard(limit = 50): Observable<LeaderboardUser[]> {
    const params = new HttpParams().set('limit', limit.toString());
    return this.http.get<LeaderboardUser[]>(`${this.baseUrl}/monthly`, { params });
  }

  /** Fetch current user's seasonal standing and rank */
  getCurrentUserRank(): Observable<LeaderboardUser> {
    return this.http.get<LeaderboardUser>(`${this.baseUrl}/user-rank`);
  }

  /** Fetch top 3 champions from the previous month */
  getLastMonthChampions(limit = 3): Observable<LeaderboardUser[]> {
    const params = new HttpParams().set('limit', limit.toString());
    return this.http.get<LeaderboardUser[]>(`${this.baseUrl}/previous-month`, { params });
  }

  /** Fetch current user's past monthly XP & rank history */
  getUserHistory(limit = 6): Observable<UserHistoryItem[]> {
    const params = new HttpParams().set('limit', limit.toString());
    return this.http.get<UserHistoryItem[]>(`${this.baseUrl}/user-history`, { params });
  }
}