import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { 
  ChallengeService, 
  UserChallengeResponseDto, 
  ChallengeCategory 
} from './challenges.service';

export type ChallengeTab = 'ACTIVE' | 'UPCOMING' | 'JOINED' | 'COMPLETED' | 'EXPIRED';

@Component({
  selector: 'app-challenges',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './challenges.html',
  styleUrls: ['./challenges.css']
})
export class ChallengesComponent implements OnInit {
  // Tab Navigation — "ACTIVE" (open, joinable challenges) is the default
  // landing view instead of a catch-all "All Challenges" tab.
  readonly tabs: ChallengeTab[] = ['ACTIVE', 'UPCOMING', 'JOINED', 'COMPLETED', 'EXPIRED'];
  activeTab: ChallengeTab = 'ACTIVE';

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
    this.currentPage = 1;
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
    const filtered = this.challengeItems.filter((item) => {
      const ch = item.challenge;
      const isJoined = item.userStatus === 'JOINED' || item.userStatus === 'IN_PROGRESS';
      const isCompleted = item.userStatus === 'COMPLETED';
      const isExpired = item.userStatus === 'EXPIRED';

      // Tab filter
      if (this.activeTab === 'ACTIVE') {
        // ch.status === 'ACTIVE' means the challenge itself is still open
        // (within its date window) — but that's not enough on its own.
        // Once this user's own participation has resolved to COMPLETED or
        // EXPIRED, it belongs in those tabs only, even if the challenge
        // window is technically still running for other participants.
        if (ch.status !== 'ACTIVE') return false;
        if (isCompleted || isExpired) return false;
      }
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

    // Completed/Expired tabs: most recently resolved first. completedAt is
    // set on both outcomes (see UserChallengeService) — items without it
    // (pre-existing rows resolved before that field was tracked for expiry)
    // sort to the bottom instead of breaking the order.
    if (this.activeTab === 'COMPLETED' || this.activeTab === 'EXPIRED') {
      filtered.sort((a, b) => {
        const aTime = a.completedAt ? new Date(a.completedAt).getTime() : 0;
        const bTime = b.completedAt ? new Date(b.completedAt).getTime() : 0;
        return bTime - aTime;
      });
    }

    // Upcoming tab: soonest-starting challenge first. If two challenges
    // start on the same date, the one ending sooner (closer to now) goes
    // first. If both dates tie too, leave their relative order as-is.
    if (this.activeTab === 'UPCOMING') {
      filtered.sort((a, b) => {
        const aStart = new Date(a.challenge.startDate).getTime();
        const bStart = new Date(b.challenge.startDate).getTime();
        if (aStart !== bStart) return aStart - bStart;

        const aEnd = new Date(a.challenge.endDate).getTime();
        const bEnd = new Date(b.challenge.endDate).getTime();
        return aEnd - bEnd;
      });
    }

    // Active tab: challenges not yet joined (and still joinable) come
    // first, joined/in-progress ones after, and not-yet-joined challenges
    // whose join window has already closed go last of all — otherwise a
    // user who's joined everything they still could ends up staring at a
    // "Join Window Closed" card at the very top, which looks broken.
    // Within each group: most recently started first, ties broken by
    // closer end date.
    if (this.activeTab === 'ACTIVE') {
      const groupRank = (item: UserChallengeResponseDto): number => {
        if (item.userStatus === 'NOT_JOINED') {
          return item.challenge.joinWindowClosed ? 2 : 0;
        }
        return 1; // JOINED / IN_PROGRESS
      };

      filtered.sort((a, b) => {
        const aRank = groupRank(a);
        const bRank = groupRank(b);
        if (aRank !== bRank) return aRank - bRank;

        const aStart = new Date(a.challenge.startDate).getTime();
        const bStart = new Date(b.challenge.startDate).getTime();
        if (aStart !== bStart) return bStart - aStart; // newer start date first

        const aEnd = new Date(a.challenge.endDate).getTime();
        const bEnd = new Date(b.challenge.endDate).getTime();
        return aEnd - bEnd; // closer end date first
      });
    }

    // Joined tab: Activity-based challenges first, Carbon-reduction ones
    // after. Within Activity-based: more progress first, ties broken by
    // closer end date. Within Carbon-reduction: less budget remaining
    // first (closer to breaching = more urgent), ties broken by later end
    // date — deliberately the opposite tie-break direction from the
    // Activity-based group.
    if (this.activeTab === 'JOINED') {
      filtered.sort((a, b) => {
        const aActivity = a.challenge.challengeType === 'ACTIVITY_BASED' ? 0 : 1;
        const bActivity = b.challenge.challengeType === 'ACTIVITY_BASED' ? 0 : 1;
        if (aActivity !== bActivity) return aActivity - bActivity;

        const aEnd = new Date(a.challenge.endDate).getTime();
        const bEnd = new Date(b.challenge.endDate).getTime();

        if (a.challenge.challengeType === 'ACTIVITY_BASED') {
          const aProgress = a.completionPercentage ?? 0;
          const bProgress = b.completionPercentage ?? 0;
          if (aProgress !== bProgress) return bProgress - aProgress; // more progress first
          return aEnd - bEnd; // closer end date first
        } else {
          const aRemaining = (a.effectiveTarget ?? 0) - (a.currentValue ?? 0);
          const bRemaining = (b.effectiveTarget ?? 0) - (b.currentValue ?? 0);
          if (aRemaining !== bRemaining) return aRemaining - bRemaining; // less budget remaining first
          return bEnd - aEnd; // later end date first
        }
      });
    }

    return filtered;
  }

  // ── Pagination ── Completed/Expired only. These are read-only history
  // tabs that can only grow over time (unlike Active/Upcoming/Joined,
  // which stay naturally small — a challenge leaves those tabs the moment
  // it resolves), so they're the ones that actually need paging as a
  // user's history accumulates.
  //
  // Page size is 9 rather than 10: these are large image cards
  // (grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)) inside
  // a max-width: 1200px container), which works out to 3 columns in
  // practice — 9 fills exactly 3 full rows with no dangling partial row.
  private readonly pageSize = 9;
  currentPage = 1;

  get isHistoryTab(): boolean {
    return this.activeTab === 'COMPLETED' || this.activeTab === 'EXPIRED';
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredChallenges.length / this.pageSize));
  }

  get safeCurrentPage(): number {
    return Math.min(this.currentPage, this.totalPages);
  }

  get pageNumbers(): number[] {
    const total = this.totalPages;
    const current = this.safeCurrentPage;
    const windowSize = Math.min(3, total);

    let start = current - 1;
    let end = current + 1;

    if (start < 1) {
      start = 1;
      end = start + windowSize - 1;
    }
    if (end > total) {
      end = total;
      start = end - windowSize + 1;
    }

    return Array.from({ length: end - start + 1 }, (_, i) => start + i);
  }

  /** Sliced for Completed/Expired; every other tab renders unpaginated, same as before. */
  get pagedChallenges(): UserChallengeResponseDto[] {
    if (!this.isHistoryTab) {
      return this.filteredChallenges;
    }
    const start = (this.safeCurrentPage - 1) * this.pageSize;
    return this.filteredChallenges.slice(start, start + this.pageSize);
  }

  goToPage(page: number): void {
    this.currentPage = Math.min(Math.max(1, page), this.totalPages);
    this.cdr.markForCheck();
  }

  prevPage(): void {
    this.goToPage(this.safeCurrentPage - 1);
  }

  nextPage(): void {
    this.goToPage(this.safeCurrentPage + 1);
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