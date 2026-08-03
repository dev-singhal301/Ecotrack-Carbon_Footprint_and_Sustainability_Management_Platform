import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ChallengeCategory = 'OVERALL' | 'TRANSPORTATION' | 'ELECTRICITY' | 'FOOD' | 'WASTE' | 'WATER' | 'FUEL';
export type ChallengeType = 'CARBON_REDUCTION' | 'ACTIVITY' | 'HABIT';
export type TrackingType = 'AUTOMATIC' | 'MANUAL';
export type UserChallengeStatus = 'NOT_JOINED' | 'JOINED' | 'IN_PROGRESS' | 'COMPLETED' | 'EXPIRED' | 'CANCELLED';

export interface ChallengeDto {
  challengeId: number;
  title: string;
  description: string;
  category: ChallengeCategory;
  challengeType: ChallengeType;
  trackingType: TrackingType;
  targetValue: number;
  unit: string;
  rewardXp: number;
  badgeImageUrl: string;
  cardImageUrl: string;
  startDate: string;
  endDate: string;
  createdBy: number;
  cancelled: boolean;
  status?: 'ACTIVE' | 'UPCOMING' | 'COMPLETED' | 'EXPIRED';
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
}