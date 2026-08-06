import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { 
  ChallengeService, 
  UserChallengeResponseDto, 
  ChallengeCategory 
} from './challenges.service';

export type ChallengeTab = 'ALL' | 'ACTIVE' | 'UPCOMING' | 'JOINED' | 'COMPLETED' | 'EXPIRED';

@Component({
  selector: 'app-challenges',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './challenges.html',
  styleUrls: ['./challenges.css']
})
export class ChallengesComponent implements OnInit {
  // Tab Navigation
  readonly tabs: ChallengeTab[] = ['ALL', 'ACTIVE', 'UPCOMING', 'JOINED', 'COMPLETED', 'EXPIRED'];
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

  // Per-card state for activity logging (DISTANCE/DURATION amount inputs,
  // and in-flight flags keyed by challengeId). "Already logged today" is
  // NOT tracked here anymore — it comes from the server on every load via
  // item.loggedToday, so it survives navigating away and back instead of
  // resetting whenever this component is re-created.
  logAmounts: Record<number, number | null> = {};
  loggingInFlight = new Set<number>();

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

  /** True for a challenge the user can currently log an activity event against. */
  canLogActivity(item: UserChallengeResponseDto): boolean {
    return item.challenge.challengeType === 'ACTIVITY_BASED' && item.userStatus === 'IN_PROGRESS';
  }

  isCarbonType(item: UserChallengeResponseDto): boolean {
    return item.challenge.challengeType === 'CARBON_REDUCTION';
  }

  /** COUNT/BOOLEAN types are a single tap — no amount to type in. */
  isQuickLogType(item: UserChallengeResponseDto): boolean {
    return item.challenge.trackingType === 'COUNT' || item.challenge.trackingType === 'BOOLEAN';
  }

  quickLogLabel(item: UserChallengeResponseDto): string {
    return item.challenge.trackingType === 'BOOLEAN' ? 'Mark Done Today' : 'Log +1 Today';
  }

  /** COUNT/BOOLEAN: one tap, no amount, capped server-side at once per day. */
  logQuickActivity(item: UserChallengeResponseDto): void {
    this.submitActivityLog(item);
  }

  /** DISTANCE/DURATION: submit whatever the user typed into that card's input. */
  logAmountActivity(item: UserChallengeResponseDto): void {
    const challengeId = item.challenge.challengeId;
    const amount = this.logAmounts[challengeId];
    if (!amount || amount <= 0) {
      alert('Enter an amount greater than zero before logging.');
      return;
    }
    this.submitActivityLog(item, amount);
  }

  private submitActivityLog(item: UserChallengeResponseDto, amount?: number): void {
    const challengeId = item.challenge.challengeId;
    if (this.loggingInFlight.has(challengeId)) return;

    this.loggingInFlight.add(challengeId);
    this.challengeService.logActivity(challengeId, amount).subscribe({
      next: (updated) => {
        const index = this.challengeItems.findIndex(
          i => i.challenge.challengeId === challengeId
        );
        if (index !== -1) {
          this.challengeItems[index] = updated;
        }
        this.logAmounts[challengeId] = null;
        this.loggingInFlight.delete(challengeId);
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Error logging activity:', err);
        // A 409 here almost always means the once-per-day cap already
        // tripped server-side — reflect that in the UI so the button
        // disables itself instead of letting the user hit it again.
        if (err.status === 409) {
          const index = this.challengeItems.findIndex(
            i => i.challenge.challengeId === challengeId
          );
          if (index !== -1) {
            this.challengeItems[index] = { ...this.challengeItems[index], loggedToday: true };
          }
        }
        alert(err.error?.message || 'Failed to log activity.');
        this.loggingInFlight.delete(challengeId);
        this.cdr.markForCheck();
      }
    });
  }

  get filteredChallenges(): UserChallengeResponseDto[] {
    return this.challengeItems.filter((item) => {
      const ch = item.challenge;
      const isJoined = item.userStatus === 'JOINED' || item.userStatus === 'IN_PROGRESS';
      const isCompleted = item.userStatus === 'COMPLETED';
      const isExpired = item.userStatus === 'EXPIRED';

      // Tab filter
      if (this.activeTab === 'ACTIVE' && ch.status !== 'ACTIVE') return false;
      if (this.activeTab === 'UPCOMING' && ch.status !== 'UPCOMING') return false;
      if (this.activeTab === 'JOINED' && !isJoined) return false;
      if (this.activeTab === 'COMPLETED' && !isCompleted) return false;
      if (this.activeTab === 'EXPIRED' && !isExpired) return false;

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