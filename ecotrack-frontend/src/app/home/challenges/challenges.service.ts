import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Challenge, UserChallenge } from '../../../types/challenge';
import { CARD_HEADER_IMAGES, CHALLENGE_BADGES } from '../../../constants/challenge-assets';

@Injectable({
  providedIn: 'root',
})
export class ChallengeService {
  // Mock list of challenges available to all users
  private mockChallenges: Challenge[] = [
    {
      challengeId: 1,
      title: 'Bike to Work Challenge',
      description: 'Ditch the car and cycle to work or school to reduce urban carbon emissions.',
      category: 'TRANSPORTATION',
      challengeType: 'ACTIVITY_BASED',
      trackingType: 'DISTANCE',
      targetValue: 30,
      unit: 'km',
      rewardXp: 150,
      cardImageUrl: CARD_HEADER_IMAGES[0].url,
      badgeImageUrl: CHALLENGE_BADGES[0].url,
      startDate: '2026-07-20',
      endDate: '2026-08-10',
      createdBy: 1,
      cancelled: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      status: 'ACTIVE',
    },
    {
      challengeId: 2,
      title: 'Meatless Week Challenge',
      description: 'Switch to plant-based meals for a week to decrease dietary footprints.',
      category: 'FOOD',
      challengeType: 'ACTIVITY_BASED',
      trackingType: 'COUNT',
      targetValue: 7,
      unit: 'days',
      rewardXp: 200,
      cardImageUrl: CARD_HEADER_IMAGES[1].url,
      badgeImageUrl: CHALLENGE_BADGES[1].url,
      startDate: '2026-08-01',
      endDate: '2026-08-15',
      createdBy: 1,
      cancelled: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      status: 'UPCOMING',
    },
    {
      challengeId: 3,
      title: '20% Energy Reduction',
      description: 'Lower your household electricity consumption compared to your 30-day baseline.',
      category: 'ELECTRICITY',
      challengeType: 'CARBON_REDUCTION',
      trackingType: null,
      targetValue: 20,
      unit: '%',
      rewardXp: 250,
      cardImageUrl: CARD_HEADER_IMAGES[2].url,
      badgeImageUrl: CHALLENGE_BADGES[2].url,
      startDate: '2026-07-01',
      endDate: '2026-07-28',
      createdBy: 1,
      cancelled: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      status: 'ENDED',
    },
  ];

  // Mock list of challenges the logged-in user has joined
  private mockUserChallenges: UserChallenge[] = [
    {
      userChallengeId: 101,
      userId: 1,
      challengeId: 1, // Joined challenge 1
      currentValue: 18,
      completionPercentage: 60,
      status: 'IN_PROGRESS',
      joinedAt: '2026-07-21T09:00:00Z',
    },
  ];

  /** Get all public challenges */
  getChallenges(): Observable<Challenge[]> {
    return of(this.mockChallenges);
  }

  /** Get challenges joined by the active user */
  getUserChallenges(userId: number): Observable<UserChallenge[]> {
    return of(this.mockUserChallenges);
  }

  /** Mock joining a challenge */
  joinChallenge(challengeId: number, userId: number): Observable<UserChallenge> {
    const newParticipation: UserChallenge = {
      userChallengeId: Date.now(),
      userId,
      challengeId,
      currentValue: 0,
      completionPercentage: 0,
      status: 'JOINED',
      joinedAt: new Date().toISOString(),
    };
    this.mockUserChallenges.push(newParticipation);
    return of(newParticipation);
  }
}