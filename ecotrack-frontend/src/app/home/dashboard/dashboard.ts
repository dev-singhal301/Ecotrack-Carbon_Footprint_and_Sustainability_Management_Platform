import { Component, OnInit } from '@angular/core';
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
  maxCategoryEmission = 0;

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
    private router: Router
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

    this.dashboardService.getSummary().subscribe({
      next: (summary) => {
        this.summary = summary;

        // Defensive copy in case categoryImpacts is null/undefined
        const impacts = summary?.categoryImpacts ? [...summary.categoryImpacts] : [];
        this.rankedCategoryImpacts = impacts.sort((a, b) => b.totalEmissions - a.totalEmissions);

        // Find highest single emission value to scale progress bars visually
        this.maxCategoryEmission = this.rankedCategoryImpacts.length > 0 
          ? Math.max(...this.rankedCategoryImpacts.map(c => c.totalEmissions)) 
          : 0;

        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load dashboard summary:', err);
        this.dataError = err.error?.message || 'Could not load your dashboard. Please try again.';
        this.isLoading = false;
      }
    });
  }

  goToTracking(): void {
    this.router.navigate(['/home/tracking']);
  }

  getCategoryLabel(category: string): string {
    return this.categoryLabels[category] ?? category;
  }

  // Returns bar fill % relative to the top emitting category
  getCategoryBarWidth(emissions: number): number {
    if (this.maxCategoryEmission <= 0) {
      return 0;
    }
    return Math.min(100, Math.max(0, (emissions / this.maxCategoryEmission) * 100));
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
      return 'var(--critical)';
    }
    if (this.getGoalFillPercent(goal) >= 85) {
      return 'var(--warning)';
    }
    return 'var(--eco-safe)';
  }
}

export { Dashboard as DashboardComponent };