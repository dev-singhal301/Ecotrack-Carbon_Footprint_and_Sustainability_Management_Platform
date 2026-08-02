import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface UserChallengeResponseDto {
  userChallengeId?: number;
  challenge: {
    challengeId: number;
    title: string;
    description: string;
    category: any;
    challengeType: any;
    trackingType: any;
    targetValue: number;
    unit: string;
    rewardXp: number;
    badgeImageUrl: string;
    cardImageUrl: string;
    startDate: string;
    endDate: string;
    createdBy: number;
    cancelled: boolean;
    status?: any;
  };
  userStatus: 'NOT_JOINED' | 'JOINED' | 'IN_PROGRESS' | 'COMPLETED' | 'EXPIRED' | 'CANCELLED';
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
  private readonly baseUrl = 'http://localhost:8080/api/user-challenges';

  constructor(private http: HttpClient) {}

  /** Fetch all challenges and current user statuses */
  getUserChallenges(): Observable<UserChallengeResponseDto[]> {
    return this.http.get<UserChallengeResponseDto[]>(this.baseUrl);
  }

  /** Join an active challenge */
  joinChallenge(challengeId: number): Observable<UserChallengeResponseDto> {
    return this.http.post<UserChallengeResponseDto>(`${this.baseUrl}/${challengeId}/join`, {});
  }

  /** Claim XP reward for completed challenge */
  claimReward(challengeId: number): Observable<UserChallengeResponseDto> {
    return this.http.post<UserChallengeResponseDto>(`${this.baseUrl}/${challengeId}/claim`, {});
  }
}