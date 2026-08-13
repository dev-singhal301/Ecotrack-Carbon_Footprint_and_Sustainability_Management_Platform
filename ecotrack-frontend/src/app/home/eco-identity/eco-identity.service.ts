import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
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
  userRank: string | number;
  memberSince: string;
  goalsAccomplished: number;
  challengesAccomplished: number;
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

  fetchProfile(): Observable<UserProfileData> {
    return this.http.get<UserProfileData>(`${this.baseUrl}/profile`).pipe(
      tap((profile) => this.profileSubject.next(profile))
    );
  }

  updateName(newName: string): Observable<UserProfileData> {
    return this.http.patch<UserProfileData>(`${this.baseUrl}/name`, { name: newName }).pipe(
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

    return this.http.post<{ avatarUrl: string }>(`${this.baseUrl}/avatar`, formData).pipe(
      tap((res) => {
        const current = this.profileSubject.value;
        if (current) {
          this.profileSubject.next({ ...current, avatarUrl: res.avatarUrl });
        }
      })
    );
  }
}