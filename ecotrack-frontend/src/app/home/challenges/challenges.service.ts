import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ChallengeCategory = 'OVERALL' | 'TRANSPORTATION' | 'ELECTRICITY' | 'FOOD' | 'WASTE' | 'WATER' | 'FUEL';
export type ChallengeType = 'CARBON_REDUCTION' | 'ACTIVITY_BASED';
export type TrackingType = 'COUNT' | 'DISTANCE' | 'DURATION' | 'BOOLEAN';
export type UserChallengeStatus = 'NOT_JOINED' | 'JOINED' | 'IN_PROGRESS' | 'COMPLETED' | 'EXPIRED' | 'CANCELLED';

export interface ChallengeDto {
  challengeId: number;
  title: string;
  description: string;
  category: ChallengeCategory;
  challengeType: ChallengeType;
  trackingType?: TrackingType;
  targetValue: number;
  unit: string;
  rewardXp: number;
  badgeImageUrl: string;
  cardImageUrl: string;
  startDate: string;
  endDate: string;
  createdBy: number;
  cancelled: boolean;
  // Matches backend ChallengeResponse.deriveStatus() exactly — a
  // challenge's own lifecycle, not the user's participation state
  // (that's UserChallengeStatus below: JOINED/COMPLETED/EXPIRED/etc).
  status?: 'ACTIVE' | 'UPCOMING' | 'ENDED' | 'CANCELLED';
  // True once start_date + JOIN_WINDOW_DAYS (2 days) has passed — the
  // challenge can still be ACTIVE, but joining it is no longer allowed.
  // Computed server-side in ChallengeResponse so the 2-day rule lives in
  // exactly one place (UserChallengeService.joinChallenge enforces it).
  joinWindowClosed?: boolean;
}

export interface UserChallengeResponseDto {
  userChallengeId?: number;
  challenge: ChallengeDto;
  userStatus: UserChallengeStatus;
  currentValue: number;
  completionPercentage: number;
  baselineValue?: number;
  effectiveTarget?: number;
  joinedAt?: string;
  completedAt?: string;
  /**
   * COUNT/BOOLEAN activity challenges only. True once today's single
   * allowed log has already been submitted — comes straight from the
   * server (activity_logs table), so it's correct on page load/reload,
   * not just within the current session.
   */
  loggedToday?: boolean | null;
}

@Injectable({
  providedIn: 'root',
})
export class ChallengeService {
  private readonly baseUrl = `${environment.apiUrl || 'http://localhost:8080'}/api/user-challenges`;

  constructor(private http: HttpClient) {}

  /** Fetch all challenges and current user status */
  getUserChallenges(): Observable<UserChallengeResponseDto[]> {
    return this.http.get<UserChallengeResponseDto[]>(this.baseUrl);
  }

  /** Join an active challenge */
  joinChallenge(challengeId: number): Observable<UserChallengeResponseDto> {
    return this.http.post<UserChallengeResponseDto>(`${this.baseUrl}/${challengeId}/join`, {});
  }

  /** Claim XP reward for a completed challenge */
  claimReward(challengeId: number): Observable<UserChallengeResponseDto> {
    return this.http.post<UserChallengeResponseDto>(`${this.baseUrl}/${challengeId}/claim`, {});
  }

  /**
   * Log one activity event for an ACTIVITY_BASED challenge.
   * Omit `amount` for COUNT/BOOLEAN tracking types — the server always
   * logs exactly 1 for those and ignores any client-supplied amount.
   * Required for DISTANCE/DURATION types.
   */
  logActivity(challengeId: number, amount?: number): Observable<UserChallengeResponseDto> {
    const body = amount !== undefined ? { amount } : {};
    return this.http.post<UserChallengeResponseDto>(`${this.baseUrl}/${challengeId}/log-activity`, body);
  }
}