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

export interface BadgeItem {
  badgeId?: number;
  id?: number | string;
  name: string;
  description?: string;
  category?: 'CHALLENGE' | 'LEADERBOARD' | 'MILESTONE' | string;
  iconUrl?: string;
  awardedAt?: string | null;
  requiredXp?: number;

  // Client-side & level locking filters (aligned with backend UserBadgeResponse)
  unlocked?: boolean;
  isUnlocked?: boolean;
  requiredLevel?: number;
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

  // Only user-uploaded content (avatars) actually lives on the backend and needs
  // its origin rewritten. Badge icons (/badges/..., /assets/badges/...) are static
  // assets bundled with the Angular app itself — leave those paths untouched so
  // they resolve against the frontend's own origin.
  private resolveBackendUrl(url: string | undefined | null): string {
    if (!url) return url ?? '';
    if (/^https?:\/\//i.test(url)) return url; // already absolute
    if (url.startsWith('/uploads')) {
      return `${this.apiRoot}${url}`;
    }
    return url;
  }

  fetchProfile(): Observable<UserProfileData> {
    return this.http.get<UserProfileData>(this.baseUrl, { headers: this.getAuthHeaders() }).pipe(
      tap((profile) => {
        profile.avatarUrl = this.resolveBackendUrl(profile.avatarUrl);

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

  getUserBadges(): Observable<BadgeItem[]> {
    return this.http.get<BadgeItem[]>(`${this.baseUrl}/badges`, {
      headers: this.getAuthHeaders(),
    }).pipe(
      tap((badges) => {
        badges.forEach((badge) => {
          badge.iconUrl = this.resolveBackendUrl(badge.iconUrl);
          // Synchronize unlocked and isUnlocked fields for seamless consumption across components
          if (badge.unlocked !== undefined && badge.isUnlocked === undefined) {
            badge.isUnlocked = badge.unlocked;
          } else if (badge.isUnlocked !== undefined && badge.unlocked === undefined) {
            badge.unlocked = badge.isUnlocked;
          }
        });
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
        const fullAvatarUrl = this.resolveBackendUrl(res.avatarUrl);

        const current = this.profileSubject.value;
        if (current) {
          this.profileSubject.next({ ...current, avatarUrl: fullAvatarUrl });
        }
      })
    );
  }
}