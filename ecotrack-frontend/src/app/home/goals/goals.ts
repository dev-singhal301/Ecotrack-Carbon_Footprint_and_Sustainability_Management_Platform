import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { forkJoin, Observable, of } from 'rxjs';
import { switchMap, map } from 'rxjs/operators';
import { GoalService, GoalResponse, GoalProgressResponse, GoalRequest } from './goals.service';

type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'EXPIRED';

interface GoalWithProgress extends GoalResponse {
  progress?: GoalProgressResponse;
  daysLeft?: number;
}

@Component({
  selector: 'app-goals',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './goals.html',
  styleUrls: ['./goals.css']
})
export class Goals implements OnInit {

  readonly arcLength = Math.PI * 80;

  readonly trackLabels: Record<string, string> = {
    REDUCE_CARBON_EMISSIONS: 'Overall Carbon Emissions (kg CO₂)',
    REDUCE_ELECTRICITY_CONSUMPTION: 'Electricity Consumption (kWh)',
    REDUCE_WATER_USAGE: 'Water Usage (Liters)',
    REDUCE_TRANSPORTATION_EMISSIONS: 'Transit Distance (km)',
    REDUCE_FUEL_CONSUMPTION: 'Fuel Usage (Liters)',
    REDUCE_FOOD_EMISSIONS: 'Food Footprint (kg CO₂)',
    REDUCE_WASTE_GENERATION: 'Waste Generation (kg)'
  };

  readonly trackTypes = Object.keys(this.trackLabels);

  minSelectableDate: string = '';
  maxSelectableDate: string = '';
  private readonly baseDailyXp = 10;

  goalForm = new FormGroup({
    goalType: new FormControl('', Validators.required),
    targetValue: new FormControl<number | null>(null, [Validators.required, Validators.min(0.01)]),
    endDate: new FormControl('', Validators.required)
  });

  activeTab: GoalStatus = 'ACTIVE';
  displayedGoals: GoalWithProgress[] = [];
  isLoading = false;
  isSubmitting = false;
  formError: string | null = null;
  dataError: string | null = null;
  activeGoalTypesReady = false;

  private activeGoalsCache: GoalWithProgress[] = [];
  private activeGoalTypes = new Set<string>();

  constructor(
    private goalService: GoalService,
    private cdr: ChangeDetectorRef
  ) {
    this.calculateFormDateBounds();
  }

  ngOnInit(): void {
    this.loadGoals(this.activeTab);
  }

  private calculateFormDateBounds(): void {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    this.minSelectableDate = this.formatDate(tomorrow);

    const maxLimit = new Date();
    maxLimit.setDate(maxLimit.getDate() + 90);
    this.maxSelectableDate = this.formatDate(maxLimit);
  }

  setActiveTab(tab: GoalStatus): void {
    if (this.activeTab === tab) {
      return;
    }
    this.activeTab = tab;
    this.loadGoals(tab);
  }

  loadGoals(status: GoalStatus): void {
    this.isLoading = true;
    this.dataError = null;
    this.cdr.detectChanges();

    this.refreshActiveGoalsCache$().subscribe(() => {
      if (status !== this.activeTab) {
        return;
      }

      if (status === 'ACTIVE') {
        this.displayedGoals = this.activeGoalsCache;
        this.isLoading = false;
        this.cdr.detectChanges();
        return;
      }

      this.goalService.getGoals(status).subscribe({
        next: (goals) => {
          if (status !== this.activeTab) {
            return;
          }
          this.loadProgressForGoals(goals, status);
        },
        error: (err) => {
          if (status !== this.activeTab) {
            return;
          }
          console.error('Failed to load goals:', err);
          this.displayedGoals = [];
          this.isLoading = false;
          this.dataError = err.error?.message || 'Could not load your goals. Please try again.';
          this.cdr.detectChanges();
        }
      });
    });
  }

  private refreshActiveGoalsCache$(): Observable<void> {
    return this.goalService.getGoals('ACTIVE').pipe(
      switchMap(activeGoals => {
        if (activeGoals.length === 0) {
          this.activeGoalsCache = [];
          this.activeGoalTypes = new Set();
          this.activeGoalTypesReady = true;
          return of(void 0);
        }

        const requests = activeGoals.map(g => this.goalService.getGoalProgress(g.goalId));
        return forkJoin(requests).pipe(
          map(progresses => {
            this.activeGoalsCache = activeGoals.map((g, i) => ({
              ...g,
              progress: progresses[i],
              daysLeft: this.calculateDaysLeft(g.endDate)
            }));

            this.activeGoalTypes = new Set(
              this.activeGoalsCache.filter(g => this.isTaskActive(g)).map(g => g.goalType)
            );
            this.activeGoalTypesReady = true;
          })
        );
      })
    );
  }

  private loadProgressForGoals(goals: GoalResponse[], status: GoalStatus): void {
    let mergedGoals: GoalWithProgress[] = [...goals];

    if (status === 'EXPIRED') {
      const liveFailed = this.activeGoalsCache.filter(g => this.isTaskFailed(g));
      liveFailed.forEach(lf => {
        if (!mergedGoals.some(m => m.goalId === lf.goalId)) {
          mergedGoals.push(lf);
        }
      });
    } else if (status === 'COMPLETED') {
      const liveAccomplished = this.activeGoalsCache.filter(g => this.isTaskAccomplished(g));
      liveAccomplished.forEach(la => {
        if (!mergedGoals.some(m => m.goalId === la.goalId)) {
          mergedGoals.push(la);
        }
      });
    }

    if (mergedGoals.length === 0) {
      if (status === this.activeTab) {
        this.displayedGoals = [];
        this.isLoading = false;
        this.cdr.detectChanges();
      }
      return;
    }

    const requests: Observable<GoalProgressResponse>[] = mergedGoals.map(g => {
      return g.progress ? of(g.progress) : this.goalService.getGoalProgress(g.goalId);
    });

    forkJoin(requests).subscribe({
      next: (progresses) => {
        if (status !== this.activeTab) {
          return;
        }

        this.displayedGoals = mergedGoals.map((g, i) => ({
          ...g,
          progress: progresses[i] || g.progress,
          daysLeft: this.calculateDaysLeft(g.endDate)
        }));

        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        if (status !== this.activeTab) {
          return;
        }
        console.error('Failed to load goal progress:', err);
        this.displayedGoals = mergedGoals.map(g => ({ ...g, daysLeft: this.calculateDaysLeft(g.endDate) }));
        this.isLoading = false;
        this.dataError = 'Some progress data could not be loaded. Showing available goals only.';
        this.cdr.detectChanges();
      }
    });
  }

  isTrackLocked(goalType: string): boolean {
    return this.activeGoalTypes.has(goalType);
  }

  hasProgressData(goal: GoalWithProgress): boolean {
    return !!goal.progress;
  }

  isTaskFailed(goal: GoalWithProgress): boolean {
    if (!goal.progress) return false;
    return goal.progress.currentValue > goal.targetValue || goal.status === 'EXPIRED';
  }

  isTaskAccomplished(goal: GoalWithProgress): boolean {
    if (!goal.progress) return false;
    if (goal.status === 'COMPLETED' || goal.progress.completed) return true;
    const isTimeUp = (goal.daysLeft ?? 0) === 0;
    return isTimeUp && goal.progress.currentValue <= goal.targetValue;
  }

  isTaskActive(goal: GoalWithProgress): boolean {
    if (this.isTaskAccomplished(goal) || this.isTaskFailed(goal)) return false;
    return (goal.daysLeft ?? 0) > 0 && goal.status === 'ACTIVE';
  }

  getActiveGoalsCount(): number {
    return this.displayedGoals.filter(g => this.isTaskActive(g)).length;
  }

  getAccomplishedGoalsCount(): number {
    return this.displayedGoals.filter(g => this.isTaskAccomplished(g)).length;
  }

  getFailedGoalsCount(): number {
    return this.displayedGoals.filter(g => this.isTaskFailed(g)).length;
  }

  get calculatedDurationDays(): number {
    const chosenDateStr = this.goalForm.get('endDate')?.value;
    if (!chosenDateStr) return 0;

    const today = this.parseLocalDate(this.todayStr());
    const end = this.parseLocalDate(chosenDateStr);
    const diffTime = end.getTime() - today.getTime();
    const days = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return days <= 0 ? 1 : days;
  }

  get projectedMaxXp(): number {
    const days = this.calculatedDurationDays;
    if (days <= 0) return 0;
    let multiplier = 1.0;
    if (days >= 30) multiplier = 1.5;
    else if (days >= 14) multiplier = 1.2;
    return Math.round(this.baseDailyXp * days * multiplier);
  }

  get projectedEndDate(): string {
    return this.goalForm.get('endDate')?.value || this.addDays(this.todayStr(), 7);
  }

  onSubmit(): void {
    this.formError = null;

    if (this.goalForm.invalid || !this.activeGoalTypesReady) {
      this.goalForm.markAllAsTouched();
      return;
    }

    const { goalType, targetValue } = this.goalForm.getRawValue();

    if (this.isTrackLocked(goalType!)) {
      this.formError = 'An active budget tracking meter already exists for this category.';
      return;
    }

    const payload: GoalRequest = {
      goalName: this.trackLabels[goalType!],
      goalType: goalType!,
      targetValue: Number(targetValue),
      durationDays: this.calculatedDurationDays,
      rewardPoints: this.projectedMaxXp
    };

    this.isSubmitting = true;
    this.cdr.detectChanges();

    this.goalService.createGoal(payload).subscribe({
      next: (savedGoal) => {
        this.isSubmitting = false;

        this.goalForm.reset({
          goalType: '',
          targetValue: null,
          endDate: ''
        });

        this.activeGoalTypes.add(savedGoal.goalType);
        this.activeTab = 'ACTIVE';
        this.isLoading = false;
        this.dataError = null;

        const augmentedGoal: GoalWithProgress = {
          ...savedGoal,
          daysLeft: this.calculateDaysLeft(savedGoal.endDate)
        };

        this.activeGoalsCache = [augmentedGoal, ...this.activeGoalsCache];
        this.displayedGoals = [augmentedGoal, ...this.displayedGoals.filter(g => this.isTaskActive(g))];

        this.cdr.detectChanges();
        this.loadGoals('ACTIVE');
      },
      error: (err) => {
        this.isSubmitting = false;
        this.formError = err.error?.message || 'Failed to create the tracking goal.';
        this.cdr.detectChanges();
      }
    });
  }

  deleteGoal(goalId: number): void {
    if (!confirm('Remove this tracking meter permanently?')) {
      return;
    }

    const target = this.displayedGoals.find(g => g.goalId === goalId);

    this.goalService.deleteGoal(goalId).subscribe({
      next: () => {
        this.displayedGoals = this.displayedGoals.filter(g => g.goalId !== goalId);
        this.activeGoalsCache = this.activeGoalsCache.filter(g => g.goalId !== goalId);
        if (target && this.isTaskActive(target)) {
          this.activeGoalTypes.delete(target.goalType);
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        alert(err.error?.message || 'Failed to remove this tracking meter.');
        this.cdr.detectChanges();
      }
    });
  }

  private getMaxGaugeValue(goal: GoalWithProgress): number {
    return goal.targetValue > 0 ? goal.targetValue / 0.75 : 0;
  }

  private getRawFillPercent(goal: GoalWithProgress): number {
    if (!goal.progress) return 0;
    const maxGauge = this.getMaxGaugeValue(goal);
    if (maxGauge <= 0) return 0;
    return Math.max(0, (goal.progress.currentValue / maxGauge) * 100);
  }

  getFillPercent(goal: GoalWithProgress): number {
    return Math.min(this.getRawFillPercent(goal), 100);
  }

  getZoneColor(goal: GoalWithProgress): string {
    if (!goal.progress) return 'var(--text-secondary)';
    if (this.isTaskFailed(goal)) return 'var(--critical)';

    const currentVal = goal.progress.currentValue;
    const targetVal = goal.targetValue;

    if (currentVal >= targetVal) return 'var(--critical)';
    if (currentVal >= targetVal * 0.85) return 'var(--warning)';
    return 'var(--eco-safe)';
  }

  getStatusLabel(goal: GoalWithProgress): string {
    if (!goal.progress) return 'No Data';
    if (this.isTaskFailed(goal)) return 'LIMIT BREACHED';

    const currentVal = goal.progress.currentValue;
    const targetVal = goal.targetValue;

    if (currentVal >= targetVal * 0.85) return 'Approaching Ceiling Warning';
    return 'On Track — Eco-Safe';
  }

  getArcDashoffset(goal: GoalWithProgress): number {
    const percent = this.getFillPercent(goal);
    return this.arcLength * (1 - percent / 100);
  }

  getProjectedXp(goal: GoalWithProgress): number | null {
    if (!goal.progress) return null;
    if (this.isTaskFailed(goal)) return 0;

    const currentVal = goal.progress.currentValue;
    const targetVal = goal.targetValue;
    const maxXp = goal.rewardPoints;

    if (targetVal <= 0) {
      return currentVal === 0 ? maxXp : 0;
    }

    if (currentVal < targetVal * 0.6) return Math.round(maxXp * 1.15);
    if (currentVal <= targetVal * 0.85) return maxXp;
    return Math.round(maxXp * 0.5);
  }

  getFootprintSaved(goal: GoalWithProgress): number | null {
    if (!goal.progress) return null;
    if (goal.progress.baselineValue <= 0) return 0;
    return goal.progress.baselineValue - goal.progress.currentValue;
  }

  private calculateDaysLeft(endDateStr: string): number {
    if (!endDateStr) return 0;

    const normalizedDate = endDateStr.includes('T') ? endDateStr.split('T')[0] : endDateStr;
    const today = this.parseLocalDate(this.todayStr());
    const end = this.parseLocalDate(normalizedDate);
    const timeDiff = end.getTime() - today.getTime();
    // +1 makes this inclusive of today: on the goal's actual end date,
    // there is still one full day remaining (today itself), so daysLeft
    // should read 1, not 0. Without the +1, a goal spanning e.g. the 5th
    // through the 8th would read "0 days left" and disappear from the
    // Active tab (isTaskActive requires daysLeft > 0) a full day early,
    // on the 8th instead of the 9th.
    const days = Math.ceil(timeDiff / (1000 * 3600 * 24)) + 1;
    return days < 0 ? 0 : days;
  }

  private parseLocalDate(dateStr: string): Date {
    const [y, m, d] = dateStr.split('-').map(Number);
    return new Date(y, m - 1, d);
  }

  private todayStr(): string {
    return this.formatDate(new Date());
  }

  private addDays(dateStr: string, days: number): string {
    const d = this.parseLocalDate(dateStr);
    d.setDate(d.getDate() + days);
    return this.formatDate(d);
  }

  private formatDate(d: Date): string {
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }
}

export { Goals as GoalsComponent };