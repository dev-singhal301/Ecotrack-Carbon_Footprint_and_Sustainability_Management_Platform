import { Component, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { forkJoin, of, Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { RecommendationService } from './recommendation.service';
import { RecommendationResponse } from './recommendation.model';
import { GoalService } from './goal.service';
import { GoalResponse, GoalProgressResponse } from './goal.model';
import { FactService } from './fact.service';
import { getGoalStatus, getGoalInsightMessage, getCategoryForGoalType, GoalStatus } from './goal-insight-messages';

type EcoBotPanel = 'recommendations' | 'goals' | 'facts' | null;

interface GoalWithProgress {
  goal: GoalResponse;
  progress: GoalProgressResponse | null;
}

@Component({
  selector: 'app-ai-tips',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ai-tips.html',
  styleUrl: './ai-tips.css',
})
export class AiTips {
  activePanel: EcoBotPanel = null;

  // --- Recommendations state ---
  recommendations: RecommendationResponse | null = null;
  recLoading = false;
  recError: string | null = null;
  revealedCount = 1;

  // --- Goal Insights state ---
  goalsWithProgress: GoalWithProgress[] = [];
  goalsLoading = false;
  goalsError: string | null = null;
  expandedGoalIds = new Set<number>();

  // --- Carbon Facts state ---
  currentFact: string | null = null;
  factLoading = false;
  factError: string | null = null;

  private readonly categoryLabels: Record<string, string> = {
    TRANSPORTATION: 'Transportation',
    ELECTRICITY: 'Electricity',
    FOOD: 'Food',
    WASTE: 'Waste',
    WATER: 'Water',
    FUEL: 'Fuel',
  };

  private readonly categoryUnits: Record<string, string> = {
    TRANSPORTATION: 'km',
    ELECTRICITY: 'kWh',
    FOOD: 'kg CO\u2082',
    WASTE: 'kg',
    WATER: 'litres',
    FUEL: 'litres',
  };

  constructor(
    private router: Router,
    private recommendationService: RecommendationService,
    private goalService: GoalService,
    private factService: FactService,
    private cdr: ChangeDetectorRef
  ) {}

  selectPanel(panel: EcoBotPanel): void {
    this.activePanel = this.activePanel === panel ? null : panel;

    if (this.activePanel === 'recommendations' && !this.recommendations && !this.recLoading) {
      this.loadRecommendations();
    }

    if (this.activePanel === 'goals' && this.goalsWithProgress.length === 0 && !this.goalsLoading) {
      this.loadGoalInsights();
    }

    if (this.activePanel === 'facts' && !this.currentFact && !this.factLoading) {
      this.loadFact();
    }

    this.cdr.detectChanges();
  }

  // --- Recommendations logic ---

  loadRecommendations(): void {
    this.recLoading = true;
    this.recError = null;
    this.cdr.detectChanges();

    this.recommendationService.getRecommendations().subscribe({
      next: (res) => {
        this.recommendations = res;
        this.revealedCount = 1;
        this.recLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Failed to load recommendations:', err);
        this.recError = err.error?.message || 'Could not load your recommendations. Please try again.';
        this.recLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  refreshRecommendations(): void {
    this.recLoading = true;
    this.recError = null;
    this.cdr.detectChanges();

    this.recommendationService.refreshRecommendations().subscribe({
      next: (res) => {
        this.recommendations = res;
        this.revealedCount = 1;
        this.recLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Failed to refresh recommendations:', err);
        this.recError = err.error?.message || 'Could not refresh your recommendations. Please try again.';
        this.recLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  get revealedCategories() {
    if (!this.recommendations) return [];
    const topThree = this.recommendations.allCategoriesSorted.slice(0, 3);
    return topThree.slice(0, this.revealedCount);
  }

  get hasMoreToExplore(): boolean {
    if (!this.recommendations) return false;
    const topThree = this.recommendations.allCategoriesSorted.slice(0, 3);
    return this.revealedCount < topThree.length;
  }

  get nextCategoryLabel(): string {
    if (!this.recommendations) return '';
    const topThree = this.recommendations.allCategoriesSorted.slice(0, 3);
    const next = topThree[this.revealedCount];
    return next ? this.getCategoryLabel(next.category) : '';
  }

  exploreNext(): void {
    if (this.hasMoreToExplore) {
      this.revealedCount++;
      this.cdr.detectChanges();
    }
  }

  getTipFor(category: string) {
    return this.recommendations?.topRecommendations.find((r) => r.category === category) ?? null;
  }

  // --- Goal Insights logic ---

  loadGoalInsights(): void {
    this.goalsLoading = true;
    this.goalsError = null;
    this.cdr.detectChanges();

    this.goalService.getActiveGoals().subscribe({
      next: (goals) => {
        if (goals.length === 0) {
          this.goalsWithProgress = [];
          this.goalsLoading = false;
          this.cdr.detectChanges();
          return;
        }

        const progressCalls = goals.reduce((acc, goal) => {
          acc[goal.goalId] = this.goalService.getGoalProgress(goal.goalId).pipe(catchError(() => of(null)));
          return acc;
        }, {} as Record<number, Observable<GoalProgressResponse | null>>);

        forkJoin(progressCalls).subscribe((progressMap) => {
          this.goalsWithProgress = goals.map((goal) => ({
            goal,
            progress: progressMap[goal.goalId],
          }));
          this.goalsLoading = false;
          this.cdr.detectChanges();
        });
      },
      error: (err) => {
        console.error('Failed to load goals:', err);
        this.goalsError = err.error?.message || 'Could not load your goals. Please try again.';
        this.goalsLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  toggleGoalExpand(goalId: number): void {
    if (this.expandedGoalIds.has(goalId)) {
      this.expandedGoalIds.delete(goalId);
    } else {
      this.expandedGoalIds.add(goalId);
    }
    this.cdr.detectChanges();
  }

  isGoalExpanded(goalId: number): boolean {
    return this.expandedGoalIds.has(goalId);
  }

  getUnitFor(category: string): string {
    return this.categoryUnits[category] ?? '';
  }

  getGoalStatusFor(item: GoalWithProgress): GoalStatus | null {
    if (!item.progress) return null;

    return getGoalStatus(
      item.goal.startDate,
      item.goal.endDate,
      item.progress.completionPercentage,
      item.progress.completed
    );
  }

  getGoalInsightFor(item: GoalWithProgress): string {
    const status = this.getGoalStatusFor(item);
    if (!status) {
      return 'Progress data is temporarily unavailable for this goal.';
    }
    return getGoalInsightMessage(item.goal.goalType, status);
  }

  getGoalCategoryLabel(goalType: string): string {
    const category = getCategoryForGoalType(goalType);
    return category ? this.getCategoryLabel(category) : 'Overall';
  }

  getGoalCategoryUnit(goalType: string): string {
    const category = getCategoryForGoalType(goalType);
    return category ? this.getUnitFor(category) : '';
  }

  getGoalProgressLine(entry: GoalWithProgress): string {
    if (!entry.progress) return '';
    const unit = this.getGoalCategoryUnit(entry.goal.goalType);
    const from = unit ? `${entry.progress.baselineValue} ${unit}` : `${entry.progress.baselineValue}`;
    const to = unit ? `${entry.progress.currentValue} ${unit}` : `${entry.progress.currentValue}`;
    return `Started at ${from} → now at ${to}`;
  }

  // --- Carbon Facts logic ---

  loadFact(): void {
    this.factLoading = true;
    this.factError = null;
    this.cdr.detectChanges();

    this.factService.getRandomFact().subscribe({
      next: (res) => {
        this.currentFact = res.fact;
        this.factLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Failed to load fact:', err);
        this.factError = err.error?.message || 'Could not load a fact right now. Please try again.';
        this.factLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  refreshFact(): void {
    this.loadFact();
  }

  // --- Shared helpers ---

  getCategoryLabel(category: string): string {
    return this.categoryLabels[category] ?? category;
  }

  goToDashboard(): void {
    this.router.navigate(['/home/dashboard']);
  }
}
