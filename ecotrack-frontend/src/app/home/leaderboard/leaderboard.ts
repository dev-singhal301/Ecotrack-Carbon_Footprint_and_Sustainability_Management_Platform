import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LeaderboardService, LeaderboardUser, UserHistoryItem } from './leaderboard.service';

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './leaderboard.html',
  styleUrls: ['./leaderboard.css']
})
export class Leaderboard implements OnInit, OnDestroy {

  // Left Section Data
  top3Podium: LeaderboardUser[] = [];
  rankingsList: LeaderboardUser[] = [];

  // Right Section Cards Data
  currentUser: LeaderboardUser | null = null;
  lastMonthChampions: LeaderboardUser[] = [];
  userHistory: UserHistoryItem[] = [];

  // UI State
  isLoading = false;
  dataError: string | null = null;
  countdownText = '';
  private timerInterval?: any;

  constructor(
    private leaderboardService: LeaderboardService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadAllData();
    this.startCountdownTimer();
  }

  ngOnDestroy(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
    }
  }

  private sanitizeUser(user: LeaderboardUser): LeaderboardUser {
    const xp = user.monthlyXp ?? user.xpGained ?? user.totalXp ?? 0;
    const isZeroXp = xp === 0;

    return {
      ...user,
      monthlyXp: xp,
      userRank: isZeroXp ? 'Unranked' : (user.userRank && user.userRank.trim() !== '' ? user.userRank : 'Unranked')
    };
  }

  loadAllData(): void {
    this.isLoading = true;
    this.dataError = null;

    this.loadCurrentMonthlyLeaderboard();
    this.loadCurrentUserRank();
    this.loadLastMonthChampions();
    this.loadUserHistory();
  }

  loadCurrentMonthlyLeaderboard(): void {
    this.leaderboardService.getMonthlyLeaderboard(50).subscribe({
      next: (data) => {
        const fullList = (data || []).map(u => this.sanitizeUser(u));
        // Take top 3 for Podium, rest for rankings list
        this.top3Podium = fullList.slice(0, 3);
        this.rankingsList = fullList.slice(3);
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load monthly leaderboard:', err);
        this.dataError = 'Could not load leaderboard data.';
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  loadCurrentUserRank(): void {
    this.leaderboardService.getCurrentUserRank().subscribe({
      next: (data) => {
        this.currentUser = data ? this.sanitizeUser(data) : null;
        this.cdr.markForCheck();
      },
      error: (err) => console.error('Failed to load current user rank:', err)
    });
  }

  loadLastMonthChampions(): void {
    this.leaderboardService.getLastMonthChampions(3).subscribe({
      next: (data) => {
        this.lastMonthChampions = (data || []).map(u => this.sanitizeUser(u));
        this.cdr.markForCheck();
      },
      error: (err) => console.error('Failed to load last month champions:', err)
    });
  }

  loadUserHistory(): void {
    this.leaderboardService.getUserHistory(6).subscribe({
      next: (data) => {
        this.userHistory = (data || []).map(item => ({
          ...item,
          totalXp: item.totalXp ?? item.xpGained ?? 0
        }));
        this.cdr.markForCheck();
      },
      error: (err) => console.error('Failed to load user history:', err)
    });
  }

  getUserXp(user: LeaderboardUser | null | undefined): number {
    if (!user) return 0;
    return user.monthlyXp ?? user.xpGained ?? user.totalXp ?? 0;
  }

  getRankTitle(user: LeaderboardUser | null | undefined): string {
    if (!user) return 'Unranked';
    const xp = this.getUserXp(user);
    if (xp === 0) return 'Unranked';
    return user.userRank && user.userRank.trim() !== '' ? user.userRank : 'Unranked';
  }

  // Maps top 3 directly by array index
  get firstPlace(): LeaderboardUser | null {
    return this.top3Podium[0] || null;
  }

  get secondPlace(): LeaderboardUser | null {
    return this.top3Podium[1] || null;
  }

  get thirdPlace(): LeaderboardUser | null {
    return this.top3Podium[2] || null;
  }

  get seasonMonthLabel(): string {
    return new Date().toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
  }

  /**
   * Accepts either a LeaderboardUser object or a number ID
   */
  getAvatarUrl(userOrId: LeaderboardUser | number | null | undefined): string {
    if (!userOrId) {
      return 'https://api.dicebear.com/7.x/bottts/svg?seed=guest';
    }

    if (typeof userOrId === 'number') {
      return `https://api.dicebear.com/7.x/bottts/svg?seed=${userOrId}`;
    }

    const user = userOrId;
    if (user.avatarUrl && user.avatarUrl.trim() !== '') {
      if (user.avatarUrl.startsWith('http://') || user.avatarUrl.startsWith('https://')) {
        return user.avatarUrl;
      }
      return `http://localhost:8080${user.avatarUrl.startsWith('/') ? '' : '/'}${user.avatarUrl}`;
    }

    return `https://api.dicebear.com/7.x/bottts/svg?seed=${user.userId || user.name || 'guest'}`;
  }

  formatMonthKey(monthKey: string): string {
    if (!monthKey || !monthKey.includes('-')) return monthKey;
    const [year, month] = monthKey.split('-');
    const date = new Date(parseInt(year, 10), parseInt(month, 10) - 1, 1);
    return date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
  }

  get levelProgressPercentage(): number {
    if (!this.currentUser) return 0;
    const xp = this.getUserXp(this.currentUser);
    const xpInCurrentLevel = xp % 500;
    return Math.min(100, Math.max(0, (xpInCurrentLevel / 500) * 100));
  }

  isCurrentUserRow(user: LeaderboardUser): boolean {
    return this.currentUser?.userId === user.userId;
  }

  private startCountdownTimer(): void {
    this.updateCountdown();
    this.timerInterval = setInterval(() => this.updateCountdown(), 60000);
  }

  private updateCountdown(): void {
    const now = new Date();
    const endOfMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59);
    const msDiff = endOfMonth.getTime() - now.getTime();

    if (msDiff <= 0) {
      this.countdownText = 'Season ended';
      return;
    }

    const days = Math.floor(msDiff / (1000 * 60 * 60 * 24));
    const hours = Math.floor((msDiff / (1000 * 60 * 60)) % 24);
    const minutes = Math.floor((msDiff / (1000 * 60)) % 60);

    this.countdownText = `${days}d ${hours}h ${minutes}m remaining`;
  }
}

export { Leaderboard as LeaderboardComponent };