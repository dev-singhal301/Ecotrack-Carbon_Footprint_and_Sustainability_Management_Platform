import { Component, ChangeDetectorRef, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { RecommendationService, RecState } from './recommendation.service';
import { TopRecommendationDto } from './recommendation.model';
import { GoalInsightService, GoalInsightState } from './goal-insight.service';
import { GoalInsightResponse } from './goal-insight.model';
import { FactService } from './fact.service';

type EcoBotPanel = 'recommendations' | 'goals' | 'facts' | null;

const ANALYZING_MESSAGES = [
  'EcoBot is analyzing your latest activity…',
  'Comparing your categories…',
  'Weighing your biggest opportunities…',
  'Putting together your recommendations…',
];

// Rotating background palette for the Carbon Facts popup — CSS gradients
// rather than photos, so there's nothing to fetch/host/attribute and it
// never breaks. Cycles by fact index (not random), so consecutive
// refreshes always look visibly different from the last one.
const FACT_BACKGROUNDS = [
  'linear-gradient(135deg, #1b4332 0%, #2d6a4f 45%, #60BA75 100%)',
  'linear-gradient(135deg, #0f766e 0%, #14532d 55%, #166534 100%)',
  'linear-gradient(135deg, #164e63 0%, #1b4332 60%, #2d6a4f 100%)',
  'linear-gradient(135deg, #365314 0%, #1b4332 55%, #0f766e 100%)',
  'linear-gradient(135deg, #052e16 0%, #14532d 50%, #4d7c0f 100%)',
];

@Component({
  selector: 'app-ai-tips',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ai-tips.html',
  styleUrl: './ai-tips.css',
})
export class AiTips implements OnInit, OnDestroy {
  activePanel: EcoBotPanel = null;

  // --- Recommendations state (driven by the shared service) ---
  recState: RecState = { status: 'idle', data: null, error: null };
  showAnalyzing = false;
  analyzingIndex = 0;
  expandedCategory: string | null = null;

  private recSub?: Subscription;
  private analyzingTimer?: ReturnType<typeof setInterval>;
  private analyzingDelayTimer?: ReturnType<typeof setTimeout>;
  readonly analyzingMessages = ANALYZING_MESSAGES;

  // --- Goal Insights state (driven by GoalInsightService, same pattern) ---
  goalState: GoalInsightState = { status: 'idle', data: null, error: null };
  showGoalsAnalyzing = false;
  goalsAnalyzingIndex = 0;
  expandedGoalIds = new Set<number>();

  private goalSub?: Subscription;
  private goalsAnalyzingTimer?: ReturnType<typeof setInterval>;
  private goalsAnalyzingDelayTimer?: ReturnType<typeof setTimeout>;

  // --- Carbon Facts popup state ---
  currentFact: string | null = null;
  factLoading = false;
  factError: string | null = null;
  factKey = 0; // bumped on every load/refresh — forces the *ngFor in the
               // template to recreate its DOM node so the fade-in animation
               // replays on every refresh, not just the first load
  readonly factBackgrounds = FACT_BACKGROUNDS;

  get factSlides(): string[] {
    return this.currentFact ? [this.currentFact] : [];
  }

  get factBackground(): string {
    return this.factBackgrounds[this.factKey % this.factBackgrounds.length];
  }

  trackByFactKey = (): number => this.factKey;

  private readonly categoryLabels: Record<string, string> = {
    TRANSPORTATION: 'Transportation',
    ELECTRICITY: 'Electricity',
    FOOD: 'Food',
    WASTE: 'Waste',
    WATER: 'Water',
    FUEL: 'Fuel',
  };

  constructor(
    private router: Router,
    private recommendationService: RecommendationService,
    private goalInsightService: GoalInsightService,
    private factService: FactService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    // Safety net if someone lands directly on ai-tips without visiting the
    // dashboard first — no-op if the dashboard already kicked these off.
    this.recommendationService.ensureLoaded();
    this.goalInsightService.ensureLoaded();

    this.recSub = this.recommendationService.state$.subscribe((state) => {
      const wasLoading = this.recState.status === 'loading';
      this.recState = state;

      if (state.status === 'loading' && !wasLoading) {
        this.startAnalyzingSequence();
      }
      if (state.status !== 'loading') {
        this.stopAnalyzingSequence();
        this.expandedCategory = null;
      }
      this.cdr.detectChanges();
    });

    this.goalSub = this.goalInsightService.state$.subscribe((state) => {
      const wasLoading = this.goalState.status === 'loading';
      this.goalState = state;

      if (state.status === 'loading' && !wasLoading) {
        this.startGoalsAnalyzingSequence();
      }
      if (state.status !== 'loading') {
        this.stopGoalsAnalyzingSequence();
      }
      this.cdr.detectChanges();
    });
  }

  ngOnDestroy(): void {
    this.recSub?.unsubscribe();
    this.goalSub?.unsubscribe();
    this.stopAnalyzingSequence();
    this.stopGoalsAnalyzingSequence();
  }

  private startAnalyzingSequence(): void {
    this.showAnalyzing = false;
    this.analyzingIndex = 0;
    // Don't show the analyzing UI for a request that resolves fast (the
    // common case, since it was likely prefetched from the dashboard).
    this.analyzingDelayTimer = setTimeout(() => {
      if (this.recState.status === 'loading') {
        this.showAnalyzing = true;
        this.analyzingTimer = setInterval(() => {
          this.analyzingIndex = (this.analyzingIndex + 1) % this.analyzingMessages.length;
          this.cdr.detectChanges();
        }, 8000);
        this.cdr.detectChanges();
      }
    }, 400);
  }

  private stopAnalyzingSequence(): void {
    this.showAnalyzing = false;
    if (this.analyzingTimer) clearInterval(this.analyzingTimer);
    if (this.analyzingDelayTimer) clearTimeout(this.analyzingDelayTimer);
  }

  // Same delayed-analyzing pattern as Recommendations, independent timers/state
  // so one panel's loading indicator never depends on the other's.
  private startGoalsAnalyzingSequence(): void {
    this.showGoalsAnalyzing = false;
    this.goalsAnalyzingIndex = 0;
    this.goalsAnalyzingDelayTimer = setTimeout(() => {
      if (this.goalState.status === 'loading') {
        this.showGoalsAnalyzing = true;
        this.goalsAnalyzingTimer = setInterval(() => {
          this.goalsAnalyzingIndex = (this.goalsAnalyzingIndex + 1) % this.analyzingMessages.length;
          this.cdr.detectChanges();
        }, 8000);
        this.cdr.detectChanges();
      }
    }, 400);
  }

  private stopGoalsAnalyzingSequence(): void {
    this.showGoalsAnalyzing = false;
    if (this.goalsAnalyzingTimer) clearInterval(this.goalsAnalyzingTimer);
    if (this.goalsAnalyzingDelayTimer) clearTimeout(this.goalsAnalyzingDelayTimer);
  }

  selectPanel(panel: EcoBotPanel): void {
    this.activePanel = this.activePanel === panel ? null : panel;

    if (this.activePanel === 'facts' && !this.currentFact && !this.factLoading) {
      this.loadFact();
    }

    this.cdr.detectChanges();
  }

  closeFactsModal(): void {
    this.activePanel = null;
    this.cdr.detectChanges();
  }

  // --- Unified refresh: fires both in parallel, not chained ---
  refreshAll(): void {
    this.recommendationService.refresh();
    this.goalInsightService.refresh();
  }

  // --- Recommendations logic ---

  refreshRecommendations(): void {
    this.recommendationService.refresh();
  }

  get isZeroEmissionState(): boolean {
    return !!this.recState.data?.message;
  }

  get topRecommendations(): TopRecommendationDto[] {
    return this.recState.data?.topRecommendations ?? [];
  }

  get primaryRecommendation(): TopRecommendationDto | null {
    return this.topRecommendations[0] ?? null;
  }

  get secondaryRecommendations(): TopRecommendationDto[] {
    return this.topRecommendations.slice(1);
  }

  toggleCategoryExpand(category: string): void {
    this.expandedCategory = this.expandedCategory === category ? null : category;
    this.cdr.detectChanges();
  }

  isCategoryExpanded(category: string): boolean {
    return this.expandedCategory === category;
  }

  trackByCategory(index: number, cat: TopRecommendationDto): string {
    return cat.category;
  }

  // --- Goal Insights logic ---

  refreshGoalInsights(): void {
    this.goalInsightService.refresh();
  }

  get goalInsights(): GoalInsightResponse[] {
    return this.goalState.data?.insights ?? [];
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

  trackByGoalId(index: number, entry: GoalInsightResponse): number {
    return entry.goalId;
  }

  // --- Carbon Facts logic ---

  loadFact(): void {
    this.factLoading = true;
    this.factError = null;
    this.cdr.detectChanges();

    this.factService.getRandomFact().subscribe({
      next: (res) => {
        this.currentFact = res.fact;
        this.factKey++;
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
