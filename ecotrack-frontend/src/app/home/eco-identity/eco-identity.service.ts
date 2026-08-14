import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface UserProfileData {
  userId: number;
  name: string;
  email: string;
  avatarUrl: string;
  userLevel: number;
  currentXp: number;
  nextLevelXpTarget: number;
  rewardPoints: number;
  userRank?: string | number;
  memberSince: string;

  // 5 Profile Stat Metrics
  goalsPassed?: number;
  goalsAccomplished?: number;
  activeGoals?: number;

  challengesPassed?: number;
  challengesAccomplished?: number;
  activeChallenges?: number;

  totalEntries?: number;
  dailyLogCount?: number;

  goalsFailed?: number;
  challengesFailed?: number;
}

@Injectable({
  providedIn: 'root',
})
export class EcoIdentityService {
  private readonly http = inject(HttpClient);

  private readonly apiRoot = environment.apiUrl
    ? environment.apiUrl.replace(/\/api\/?$/, '')
    : 'http://localhost:8080';
  private readonly baseUrl = `${this.apiRoot}/api/users/me`;

  private profileSubject = new BehaviorSubject<UserProfileData | null>(null);
  public profile$ = this.profileSubject.asObservable();

  private getAuthHeaders(): HttpHeaders {
    const token =
      localStorage.getItem('ecotrack_auth_token') ||
      localStorage.getItem('token') ||
      localStorage.getItem('jwt') ||
      '';
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });
  }

  fetchProfile(): Observable<UserProfileData> {
    return this.http.get<UserProfileData>(this.baseUrl, { headers: this.getAuthHeaders() }).pipe(
      tap((profile) => {
        if (profile.avatarUrl && profile.avatarUrl.startsWith('/uploads')) {
          profile.avatarUrl = `${this.apiRoot}${profile.avatarUrl}`;
        }

        // Map fallbacks safely
        profile.activeGoals = profile.activeGoals ?? 0;
        profile.activeChallenges = profile.activeChallenges ?? 0;
        profile.totalEntries = profile.totalEntries ?? profile.dailyLogCount ?? 0;
        profile.goalsAccomplished = profile.goalsAccomplished ?? profile.goalsPassed ?? 0;
        profile.challengesAccomplished = profile.challengesAccomplished ?? profile.challengesPassed ?? 0;

        this.profileSubject.next(profile);
      })
    );
  }

  updateName(newName: string): Observable<UserProfileData> {
    return this.http.patch<UserProfileData>(
      `${this.baseUrl}/name`,
      { name: newName },
      { headers: this.getAuthHeaders() }
    ).pipe(
      tap((updated) => {
        const current = this.profileSubject.value;
        if (current) {
          this.profileSubject.next({ ...current, name: updated.name });
        }
      })
    );
  }

  uploadAvatar(file: File): Observable<{ avatarUrl: string }> {
    const formData = new FormData();
    formData.append('avatar', file);

    return this.http.post<{ avatarUrl: string }>(
      `${this.baseUrl}/avatar`,
      formData,
      { headers: this.getAuthHeaders() }
    ).pipe(
      tap((res) => {
        const fullAvatarUrl = res.avatarUrl.startsWith('/uploads')
          ? `${this.apiRoot}${res.avatarUrl}`
          : res.avatarUrl;

        const current = this.profileSubject.value;
        if (current) {
          this.profileSubject.next({ ...current, avatarUrl: fullAvatarUrl });
        }
      })
    );
  }
}