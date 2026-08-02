import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChallengeService, UserChallengeResponseDto } from './challenges.service';
import { ChallengeTab, ChallengeCategory } from '../../../types/challenge';

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
  readonly categories: (ChallengeCategory | 'ALL')[] = [
    'ALL',
    'OVERALL',
    'TRANSPORTATION',
    'ELECTRICITY',
    'FOOD',
    'WASTE',
    'WATER',
    'FUEL'
  ];

  // Combined Data List from Backend
  challengeItems: UserChallengeResponseDto[] = [];

  constructor(private challengeService: ChallengeService) {}

  ngOnInit(): void {
    this.loadData();
  }

  setActiveTab(tab: ChallengeTab): void {
    this.activeTab = tab;
  }

  joinChallenge(item: UserChallengeResponseDto): void {
    if (item.userStatus !== 'NOT_JOINED') return;

    this.challengeService.joinChallenge(item.challenge.challengeId).subscribe({
      next: (updated) => {
        const index = this.challengeItems.findIndex(i => i.challenge.challengeId === item.challenge.challengeId);
        if (index !== -1) {
          this.challengeItems[index] = updated;
        }
      },
      error: (err) => {
        console.error('Error joining challenge:', err);
        alert(err.error?.message || 'Failed to join challenge.');
      }
    });
  }

  get filteredChallenges(): UserChallengeResponseDto[] {
    return this.challengeItems.filter((item) => {
      const ch = item.challenge;
      const isJoined = item.userStatus === 'JOINED' || item.userStatus === 'IN_PROGRESS';
      const isCompleted = item.userStatus === 'COMPLETED';

      // Tab filter
      if (this.activeTab === 'ACTIVE' && ch.status !== 'ACTIVE') return false;
      if (this.activeTab === 'UPCOMING' && ch.status !== 'UPCOMING') return false;
      if (this.activeTab === 'JOINED' && !isJoined) return false;
      if (this.activeTab === 'COMPLETED' && !isCompleted) return false;

      // Category filter
      if (this.selectedCategory !== 'ALL' && ch.category !== this.selectedCategory) {
        return false;
      }

      // Search query filter
      if (this.searchQuery.trim()) {
        const query = this.searchQuery.toLowerCase();
        const matchesTitle = ch.title?.toLowerCase().includes(query);
        const matchesDesc = ch.description?.toLowerCase().includes(query);
        if (!matchesTitle && !matchesDesc) return false;
      }

      return true;
    });
  }

  private loadData(): void {
    this.challengeService.getUserChallenges().subscribe({
      next: (data) => {
        this.challengeItems = data;
      },
      error: (err) => {
        console.error('Error fetching challenges:', err);
      }
    });
  }
}