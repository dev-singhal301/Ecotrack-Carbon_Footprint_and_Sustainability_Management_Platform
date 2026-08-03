import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { 
  ChallengeService, 
  UserChallengeResponseDto, 
  ChallengeCategory 
} from './challenges.service';

export type ChallengeTab = 'ALL' | 'ACTIVE' | 'UPCOMING' | 'JOINED' | 'COMPLETED';

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

  // Data List
  challengeItems: UserChallengeResponseDto[] = [];
  isLoading = false;

  constructor(
    private challengeService: ChallengeService,
    private cdr: ChangeDetectorRef
  ) {}

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
        const index = this.challengeItems.findIndex(
          i => i.challenge.challengeId === item.challenge.challengeId
        );
        if (index !== -1) {
          this.challengeItems[index] = updated;
          this.cdr.markForCheck();
        }
      },
      error: (err) => {
        console.error('Error joining challenge:', err);
        alert(err.error?.message || 'Failed to join challenge.');
      }
    });
  }

  claimReward(item: UserChallengeResponseDto): void {
    if (item.userStatus !== 'COMPLETED') return;

    this.challengeService.claimReward(item.challenge.challengeId).subscribe({
      next: (updated) => {
        const index = this.challengeItems.findIndex(
          i => i.challenge.challengeId === item.challenge.challengeId
        );
        if (index !== -1) {
          this.challengeItems[index] = updated;
          this.cdr.markForCheck();
        }
      },
      error: (err) => {
        console.error('Error claiming reward:', err);
        alert(err.error?.message || 'Failed to claim reward.');
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
    this.isLoading = true;
    this.challengeService.getUserChallenges().subscribe({
      next: (data) => {
        this.challengeItems = data;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Error fetching challenges:', err);
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }
}