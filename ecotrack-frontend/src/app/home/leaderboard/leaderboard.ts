import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';

export interface LeaderboardUser {
  userId: number;
  name: string;
  userRank: string;
  userLevel: number;
  monthlyXp: number;
  totalXp?: number;
  xpGained?: number;
  rank: number;
}

export interface UserHistoryItem {
  monthKey: string;
  totalXp: number;
  xpGained?: number;
}

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './leaderboard.html',
  styleUrls: ['./leaderboard.css']
})
export class Leaderboard implements OnInit, OnDestroy {

  private readonly baseUrl = 'http://localhost:8080/api/leaderboard';

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
    private http: HttpClient,
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
    const params = new HttpParams().set('limit', '50');
    this.http.get<LeaderboardUser[]>(`${this.baseUrl}/monthly`, { params }).subscribe({
      next: (data) => {
        const fullList = (data || []).map(u => this.sanitizeUser(u));
        // Take top 3 for Podium, rest for rankings list
        this.top3Podium = fullList.slice(0, 3);
        this.rankingsList = fullList.slice(3);
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Failed to load monthly leaderboard:', err);
        this.dataError = 'Could not load leaderboard data.';
        this.isLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  loadCurrentUserRank(): void {
    this.http.get<LeaderboardUser>(`${this.baseUrl}/user-rank`).subscribe({
      next: (data) => {
        this.currentUser = data ? this.sanitizeUser(data) : null;
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Failed to load current user rank:', err)
    });
  }

  loadLastMonthChampions(): void {
    const params = new HttpParams().set('limit', '3');
    this.http.get<LeaderboardUser[]>(`${this.baseUrl}/previous-month`, { params }).subscribe({
      next: (data) => {
        this.lastMonthChampions = (data || []).map(u => this.sanitizeUser(u));
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Failed to load last month champions:', err)
    });
  }

  loadUserHistory(): void {
    const params = new HttpParams().set('limit', '6');
    this.http.get<UserHistoryItem[]>(`${this.baseUrl}/user-history`, { params }).subscribe({
      next: (data) => {
        this.userHistory = (data || []).map(item => ({
          ...item,
          totalXp: item.totalXp ?? item.xpGained ?? 0
        }));
        this.cdr.detectChanges();
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

  getAvatarUrl(userId: number | undefined): string {
    if (!userId) return 'https://api.dicebear.com/7.x/bottts/svg?seed=guest';
    return `https://api.dicebear.com/7.x/bottts/svg?seed=${userId}`;
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