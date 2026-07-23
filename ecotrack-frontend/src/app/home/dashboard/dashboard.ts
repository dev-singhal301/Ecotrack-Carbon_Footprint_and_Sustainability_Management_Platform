import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { DashboardService } from './dashboard.service';
import { DashboardSummary, CategoryImpact, GoalSnapshot } from './dashboard.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css']
})
export class Dashboard implements OnInit {

  summary: DashboardSummary | null = null;
  rankedCategoryImpacts: CategoryImpact[] = [];

  isLoading = false;
  dataError: string | null = null;

  private readonly xpPerLevel = 500;

  private readonly categoryLabels: Record<string, string> = {
    TRANSPORTATION: 'Transportation',
    ELECTRICITY: 'Electricity',
    FOOD: 'Food',
    WASTE: 'Waste',
    WATER: 'Water',
    FUEL: 'Fuel'
  };

  constructor(
    private dashboardService: DashboardService,
    private router: Router,
    private cdr: ChangeDetectorRef // 1. INJECT CHANGE DETECTOR
  ) {}

  ngOnInit(): void {
    this.loadSummary();
  }

  get greetingText(): string {
    return this.summary?.username ? `Hi ${this.summary.username}!` : 'Welcome back!';
  }

  get xpProgressPercent(): number {
    if (!this.summary) {
      return 0;
    }
    const levelStartXp = (this.summary.userLevel - 1) * this.xpPerLevel;
    const span = this.summary.nextLevelXpTarget - levelStartXp;
    if (span <= 0) {
      return 0;
    }
    const progress = this.summary.currentXp - levelStartXp;
    return Math.min(100, Math.max(0, (progress / span) * 100));
  }

  loadSummary(): void {
    this.isLoading = true;
    this.dataError = null;
    this.cdr.detectChanges(); // 2. FORCE IMMEDIATE INITIAL UI FEEDBACK

    this.dashboardService.getSummary().subscribe({
      next: (summary) => {
        this.summary = summary;
        this.rankedCategoryImpacts = [...summary.categoryImpacts]
          .sort((a, b) => b.totalEmissions - a.totalEmissions);
        this.isLoading = false;

        // 3. FORCE IMMEDIATE RENDER ON DATA RECEIPT
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Failed to load dashboard summary:', err);
        this.dataError = err.error?.message || 'Could not load your dashboard. Please try again.';
        this.isLoading = false;

        // 4. FORCE IMMEDIATE RENDER ON ERROR
        this.cdr.detectChanges();
      }
    });
  }

  goToTracking(): void {
    this.router.navigate(['/home/tracking']);
  }

  getCategoryLabel(category: string): string {
    return this.categoryLabels[category] ?? category;
  }

  getCategoryBarWidth(emission: number): number {
    if (!this.rankedCategoryImpacts.length) {
      return 0;
    }
    const max = Math.max(...this.rankedCategoryImpacts.map(c => c.totalEmissions));
    if (max <= 0) {
      return 0;
    }
    return (emission / max) * 100;
  }

  getGoalFillPercent(goal: GoalSnapshot): number {
    if (goal.targetValue <= 0) {
      return 0;
    }
    return Math.min(100, Math.max(0, (goal.currentValue / goal.targetValue) * 100));
  }

  getGoalStatusLabel(goal: GoalSnapshot): string {
    if (goal.currentValue > goal.targetValue) {
      return 'Over Budget';
    }
    if (this.getGoalFillPercent(goal) >= 85) {
      return 'Approaching Limit';
    }
    return 'On Track';
  }

  getGoalStatusColor(goal: GoalSnapshot): string {
    if (goal.currentValue > goal.targetValue) {
      return 'var(--eco-red)';
    }
    if (this.getGoalFillPercent(goal) >= 85) {
      return 'var(--eco-warning)';
    }
    return 'var(--eco-green)';
  }
}

export { Dashboard as DashboardComponent };