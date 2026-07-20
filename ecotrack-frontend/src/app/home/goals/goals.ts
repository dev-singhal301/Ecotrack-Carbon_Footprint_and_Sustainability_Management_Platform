import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';
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

  readonly arcLength = Math.PI * 80; // Arc radius matching path layout dimensions (R=80)

  readonly trackLabels: Record<string, string> = {
    REDUCE_CARBON_EMISSIONS: 'Overall Carbon Emissions (kg CO\u2082)',
    REDUCE_ELECTRICITY_CONSUMPTION: 'Electricity Consumption (kWh)',
    REDUCE_WATER_USAGE: 'Water Usage (Liters)',
    REDUCE_TRANSPORTATION_EMISSIONS: 'Transit Distance (km)',
    REDUCE_FUEL_CONSUMPTION: 'Fuel Usage (Liters)',
    REDUCE_FOOD_EMISSIONS: 'Food Footprint (kg CO\u2082)',
    REDUCE_WASTE_GENERATION: 'Waste Generation (kg)'
  };

  readonly trackTypes = Object.keys(this.trackLabels);
  readonly baselineWindowOptions = [7, 14, 30];

  minSelectableDate: string = '';
  maxSelectableDate: string = '';
  private readonly baseDailyXp = 10; 

  goalForm = new FormGroup({
    goalType: new FormControl('', Validators.required),
    targetValue: new FormControl<number | null>(null, [Validators.required, Validators.min(0.01)]),
    endDate: new FormControl('', Validators.required),
    baselinePeriodDays: new FormControl<number>(30, Validators.required)
  });

  activeTab: GoalStatus = 'ACTIVE';
  displayedGoals: GoalWithProgress[] = [];
  isLoading = false;
  isSubmitting = false;
  formError: string | null = null;
  dataError: string | null = null;
  activeGoalTypesReady = false;

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
    this.cdr.detectChanges(); 
  }

  loadGoals(status: GoalStatus): void {
    this.isLoading = true;
    this.dataError = null;
    this.cdr.detectChanges(); 

    // Fetching data broadly to guarantee client-side calculations sort historical states perfectly
    this.goalService.getGoals(status).subscribe({
      next: (goals) => {
        if (status !== this.activeTab) {
          return;
        }

        if (status === 'EXPIRED' || status === 'COMPLETED') {
          // Cross-load active track states to catch items whose deadlines passed but are structurally listed as active in the DB
          this.goalService.getGoals('ACTIVE').subscribe({
            next: (activeGoals) => {
              const combined = [...goals, ...activeGoals];
              const unique = combined.filter((v, i, a) => a.findIndex(t => t.goalId === v.goalId) === i);
              this.loadProgressForGoals(unique, status);
            },
            error: () => this.loadProgressForGoals(goals, status)
          });
          return;
        }

        if (status === 'ACTIVE') {
          this.activeGoalTypesReady = false; 
        }
        
        this.loadProgressForGoals(goals, status);
        this.cdr.detectChanges();
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
  }

  private loadProgressForGoals(goals: GoalResponse[], status: GoalStatus): void {
    if (goals.length === 0) {
      if (status === this.activeTab) {
        this.displayedGoals = [];
        this.isLoading = false;
        this.cdr.detectChanges();
      }
      return;
    }

    const requests = goals.map(g => this.goalService.getGoalProgress(g.goalId));

    forkJoin(requests).subscribe({
      next: (progresses) => {
        if (status !== this.activeTab) {
          return;
        }
        
        this.displayedGoals = goals.map((g, i) => ({ 
          ...g, 
          progress: progresses[i],
          daysLeft: this.calculateDaysLeft(g.endDate)
        }));

        if (status === 'ACTIVE') {
          this.activeGoalTypes = new Set(
            this.displayedGoals
              .filter(g => this.isTaskActive(g))
              .map(g => g.goalType)
          );
          this.activeGoalTypesReady = true;
        }

        this.isLoading = false;
        this.cdr.detectChanges(); 
      },
      error: (err) => {
        if (status !== this.activeTab) {
          return;
        }
        console.error('Failed to load goal progress:', err);
        this.displayedGoals = goals.map(g => ({ ...g, daysLeft: this.calculateDaysLeft(g.endDate) }));
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

  /* ----------------------------------
     CORE STATE LAWS (Emissions Verification Fix)
  ---------------------------------- */
  
  // Rule 1: It is failed IMMEDIATELY if the current value breaches the budget cap limit
  isTaskFailed(goal: GoalWithProgress): boolean {
    if (!goal.progress) return false;
    return goal.progress.currentValue > goal.targetValue;
  }

  // Rule 2: It is accomplished if the deadline is met/passed AND emissions are safely <= target limit
  isTaskAccomplished(goal: GoalWithProgress): boolean {
    if (!goal.progress) return false;
    const isTimeUp = (goal.daysLeft ?? 0) === 0 || goal.status === 'COMPLETED';
    return isTimeUp && goal.progress.currentValue <= goal.targetValue;
  }

  // Rule 3: It is genuinely active only if time remains AND it has not exceeded the ceiling limit
  isTaskActive(goal: GoalWithProgress): boolean {
    const hasTime = (goal.daysLeft ?? 0) > 0 && goal.status === 'ACTIVE';
    return hasTime && !this.isTaskFailed(goal);
  }

  /* ----------------------------------
     TAB COUNT COMPUTATIONS
  ---------------------------------- */
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
    if (!chosenDateStr) return 7; 
    
    const today = new Date(this.todayStr() + 'T00:00:00');
    const end = new Date(chosenDateStr + 'T00:00:00');
    const diffTime = end.getTime() - today.getTime();
    const days = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return days <= 0 ? 1 : days;
  }

  get projectedMaxXp(): number {
    const days = this.calculatedDurationDays;
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

    const { goalType, targetValue, endDate, baselinePeriodDays } = this.goalForm.getRawValue();

    if (this.isTrackLocked(goalType!)) {
      this.formError = 'An active budget tracking meter already exists for this category.';
      return;
    }

    const payload: GoalRequest = {
      goalName: this.trackLabels[goalType!],
      goalType: goalType!,
      targetValue: Number(targetValue),
      startDate: this.todayStr(),
      endDate: endDate!,
      rewardPoints: this.projectedMaxXp,
      baselinePeriodDays: Number(baselinePeriodDays)
    };

    this.isSubmitting = true;
    this.cdr.detectChanges();

    this.goalService.createGoal(payload).subscribe({
      next: (savedGoal) => {
        this.isSubmitting = false;
        
        this.goalForm.reset({
          goalType: '',
          targetValue: null,
          endDate: '',
          baselinePeriodDays: 30
        });

        this.activeGoalTypes.add(savedGoal.goalType);
        this.activeTab = 'ACTIVE';
        this.isLoading = false;
        this.dataError = null;

        const augmentedGoal: GoalWithProgress = {
          ...savedGoal,
          daysLeft: this.calculateDaysLeft(savedGoal.endDate)
        };

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
    
    // Scale tracking fill percentages relative to the strict 75% target line
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
    return 'On Track \u2014 Eco-Safe';
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

    // Bonus for maintaining significantly low consumption footprints
    if (currentVal < targetVal * 0.6) return Math.round(maxXp * 1.15);
    if (currentVal <= targetVal * 0.85) return maxXp;
    return Math.round(maxXp * 0.5);
  }

  getFootprintSaved(goal: GoalWithProgress): number | null {
    if (!goal.progress) return null;
    return goal.progress.baselineValue - goal.progress.currentValue;
  }

  private calculateDaysLeft(endDateStr: string): number {
    if (!endDateStr) return 0;
    const today = new Date(this.todayStr() + 'T00:00:00');
    const end = new Date(endDateStr + 'T00:00:00');
    const timeDiff = end.getTime() - today.getTime();
    const days = Math.ceil(timeDiff / (1000 * 3600 * 24));
    return days < 0 ? 0 : days;
  }

  private todayStr(): string {
    return this.formatDate(new Date());
  }

  private addDays(dateStr: string, days: number): string {
    const d = new Date(dateStr + 'T00:00:00');
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