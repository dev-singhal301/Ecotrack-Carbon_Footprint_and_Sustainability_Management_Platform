import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  Challenge,
  UserChallenge,
  ChallengeTab,
  ChallengeCategory
} from '../../../types/challenge';

@Component({
  selector: 'app-challenges',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './challenges.html',
  styleUrls: ['./challenges.css']
})
export class ChallengesComponent implements OnInit {
  // Tab Navigation
  readonly tabs: ChallengeTab[] = ['ALL', 'ACTIVE', 'UPCOMING', 'JOINED', 'COMPLETED'];
  activeTab: ChallengeTab = 'ALL';

  // Category & Search Filters
  selectedCategory: string = 'ALL';
  searchQuery: string = '';
  readonly categories: string[] = [
    'ALL',
    'TRANSPORTATION',
    'ELECTRICITY',
    'FOOD',
    'WASTE',
    'WATER',
    'FUEL'
  ];

  // Data Stores
  challenges: Challenge[] = [];
  userChallenges: UserChallenge[] = [];

  ngOnInit(): void {
    this.loadChallenges();
    this.loadUserChallenges();
  }

  /**
   * Sets the active filter tab selected in the UI.
   */
  setActiveTab(tab: ChallengeTab): void {
    this.activeTab = tab;
  }

  /**
   * Returns participation record for a given challenge ID if joined.
   */
  getParticipation(challengeId: number): UserChallenge | undefined {
    return this.userChallenges.find((uc) => uc.challengeId === challengeId);
  }

  /**
   * Joins an active challenge.
   */
  joinChallenge(challenge: Challenge): void {
    if (challenge.status !== 'ACTIVE') return;

    // Prevent duplicate joining
    if (this.getParticipation(challenge.challengeId)) return;

    const newParticipation: UserChallenge = {
      userChallengeId: Date.now(),
      userId: 101, // Active user ID
      challengeId: challenge.challengeId,
      currentValue: 0,
      completionPercentage: 0,
      status: 'IN_PROGRESS',
      joinedAt: new Date().toISOString(),
      challenge: challenge
    };

    this.userChallenges = [...this.userChallenges, newParticipation];
  }

  /**
   * Dynamic getter for filtering challenges based on Active Tab, Category, and Search Query.
   */
  get filteredChallenges(): Challenge[] {
    return this.challenges.filter((ch) => {
      const participation = this.getParticipation(ch.challengeId);

      // 1. Filter by Tab
      if (this.activeTab === 'ACTIVE' && ch.status !== 'ACTIVE') return false;
      if (this.activeTab === 'UPCOMING' && ch.status !== 'UPCOMING') return false;
      if (this.activeTab === 'JOINED' && (!participation || participation.status !== 'IN_PROGRESS')) return false;
      if (this.activeTab === 'COMPLETED' && (!participation || participation.status !== 'COMPLETED')) return false;

      // 2. Filter by Category
      if (this.selectedCategory !== 'ALL' && ch.category !== this.selectedCategory) {
        return false;
      }

      // 3. Filter by Search Query
      if (this.searchQuery.trim()) {
        const query = this.searchQuery.toLowerCase();
        const matchesTitle = ch.title.toLowerCase().includes(query);
        const matchesDesc = ch.description.toLowerCase().includes(query);
        if (!matchesTitle && !matchesDesc) return false;
      }

      return true;
    });
  }

  /**
   * Loads mock or API challenge data.
   */
  private loadChallenges(): void {
    const now = new Date().toISOString();

    this.challenges = [
      {
        challengeId: 1,
        title: 'Zero Waste Week',
        description: 'Reduce household waste generation by avoiding single-use plastics and composting organic waste.',
        category: 'WASTE',
        challengeType: 'CARBON_REDUCTION',
        trackingType: null,
        targetValue: 10,
        unit: 'kg CO2e',
        rewardXp: 150,
        badgeImageUrl: '/badges/ch_1.avif',
        cardImageUrl: '/images/ch_card_1.avif',
        startDate: '2026-07-01',
        endDate: '2026-08-15',
        createdBy: 1,
        cancelled: false,
        createdAt: now,
        updatedAt: now,
        status: 'ACTIVE'
      },
      {
        challengeId: 2,
        title: 'Commute by Bike',
        description: 'Swap car rides for cycling to work or school for a total of 50 km this month.',
        category: 'TRANSPORTATION',
        challengeType: 'ACTIVITY_BASED',
        trackingType: 'DISTANCE',
        targetValue: 50,
        unit: 'km',
        rewardXp: 200,
        badgeImageUrl: '/badges/ch_2.avif',
        cardImageUrl: '/images/ch_card_2.avif',
        startDate: '2026-07-10',
        endDate: '2026-08-20',
        createdBy: 1,
        cancelled: false,
        createdAt: now,
        updatedAt: now,
        status: 'ACTIVE'
      },
      {
        challengeId: 3,
        title: 'Plant-Based August',
        description: 'Choose plant-based meals to cut carbon emissions from food production.',
        category: 'FOOD',
        challengeType: 'CARBON_REDUCTION',
        trackingType: null,
        targetValue: 25,
        unit: 'kg CO2e',
        rewardXp: 300,
        badgeImageUrl: '/badges/ch_3.avif',
        cardImageUrl: '/images/ch_card_3.avif',
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        createdBy: 1,
        cancelled: false,
        createdAt: now,
        updatedAt: now,
        status: 'UPCOMING'
      }
    ];
  }

  /**
   * Loads user participation state.
   */
  private loadUserChallenges(): void {
    this.userChallenges = [
      {
        userChallengeId: 501,
        userId: 101,
        challengeId: 1,
        currentValue: 4.5,
        completionPercentage: 45,
        status: 'IN_PROGRESS',
        joinedAt: '2026-07-05T10:00:00Z'
      }
    ];
  }
}