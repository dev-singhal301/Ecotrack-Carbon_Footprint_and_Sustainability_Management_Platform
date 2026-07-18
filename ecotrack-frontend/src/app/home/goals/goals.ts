import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { forkJoin } from 'rxjs';

type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'EXPIRED';

interface GoalRequest {
  goalName: string;
  goalType: string;
  targetValue: number;
  startDate: string;
  endDate: string;
  rewardPoints: number;
  baselinePeriodDays: number;
}

interface GoalResponse {
  goalId: number;
  goalName: string;
  goalType: string;
  targetValue: number;
  baselineValue: number;
  rewardPoints: number;
  status: GoalStatus;
  startDate: string;
  endDate: string;
}

interface GoalProgressResponse {
  goalId: number;
  currentValue: number;
  targetValue: number;
  baselineValue: number;
  completionPercentage: number;
  completed: boolean;
}

interface GoalWithProgress extends GoalResponse {
  progress?: GoalProgressResponse;
  daysLeft?: number;
}

interface DurationTier {
  days: number;
  label: string;
  baseXp: number;
  multiplier: number;
}

@Component({
  selector: 'app-goals',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './goals.html',
  styleUrls: ['./goals.css']
})
export class Goals implements OnInit {

  private readonly baseUrl = 'http://localhost:8080/api/goals';
  readonly arcLength = Math.PI * 80; // Adjusted radius matching the new path arc (R=80)

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

  readonly durationTiers: DurationTier[] = [
    { days: 7, label: '7-Day Sprint', baseXp: 35, multiplier: 1.0 },
    { days: 14, label: '14-Day Baseline', baseXp: 70, multiplier: 1.2 },
    { days: 30, label: '30-Day Endurance Limit', baseXp: 150, multiplier: 1.5 }
  ];

  readonly baselineWindowOptions = [7, 14, 30];

  goalForm = new FormGroup({
    goalType: new FormControl('', Validators.required),
    targetValue: new FormControl<number | null>(null, [Validators.required, Validators.min(0.01)]),
    durationDays: new FormControl<number>(7, Validators.required),
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
    private http: HttpClient,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadGoals(this.activeTab);
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
    
    const params = new HttpParams().set('status', status);

    this.http.get<GoalResponse[]>(this.baseUrl, { params }).subscribe({
      next: (goals) => {
        if (status !== this.activeTab) {
          return;
        }

        if (status === 'EXPIRED') {
          const activeParams = new HttpParams().set('status', 'ACTIVE');
          this.http.get<GoalResponse[]>(this.baseUrl, { params: activeParams }).subscribe({
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
          this.activeGoalTypes = new Set(goals.filter(g => !this.isTaskFailed(g)).map(g => g.goalType));
          this.activeGoalTypesReady = true;
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

    const requests = goals.map(g =>
      this.http.get<GoalProgressResponse>(`${this.baseUrl}/${g.goalId}/progress`)
    );

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

  getActiveGoalsCount(): number {
    return this.displayedGoals.filter(g => !this.isTaskFailed(g)).length;
  }

  getAccomplishedGoalsCount(): number {
    return this.displayedGoals.filter(g => !this.isTaskFailed(g) && g.rewardPoints > 0).length;
  }

  getFailedGoalsCount(): number {
    return this.displayedGoals.filter(g => this.isTaskFailed(g) || g.rewardPoints === 0).length;
  }

  get selectedTier(): DurationTier {
    const rawValue = this.goalForm.get('durationDays')?.value;
    const days = rawValue !== null && rawValue !== undefined ? Number(rawValue) : 7;
    return this.durationTiers.find(t => t.days === days) ?? this.durationTiers[0];
  }

  get projectedMaxXp(): number {
    const tier = this.selectedTier;
    return Math.round(tier.baseXp * tier.multiplier);
  }

  get projectedEndDate(): string {
    return this.addDays(this.todayStr(), this.selectedTier.days);
  }

  onSubmit(): void {
    this.formError = null;

    if (this.goalForm.invalid || !this.activeGoalTypesReady) {
      this.goalForm.markAllAsTouched();
      return;
    }

    const { goalType, targetValue, durationDays, baselinePeriodDays } = this.goalForm.getRawValue();

    if (this.isTrackLocked(goalType!)) {
      this.formError = 'An active budget tracking meter already exists for this category.';
      return;
    }

    const tier = this.durationTiers.find(t => t.days === Number(durationDays)) ?? this.durationTiers[0];
    const startDate = this.todayStr();
    const endDate = this.addDays(startDate, tier.days);

    const payload: GoalRequest = {
      goalName: this.trackLabels[goalType!],
      goalType: goalType!,
      targetValue: Number(targetValue),
      startDate,
      endDate,
      rewardPoints: Math.round(tier.baseXp * tier.multiplier),
      baselinePeriodDays: Number(baselinePeriodDays)
    };

    this.isSubmitting = true;
    this.cdr.detectChanges();

    this.http.post<GoalResponse>(this.baseUrl, payload).subscribe({
      next: (savedGoal) => {
        this.isSubmitting = false;
        this.goalForm.reset({
          goalType: '',
          targetValue: null,
          durationDays: 7,
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

        this.displayedGoals = [augmentedGoal, ...this.displayedGoals.filter(g => g.status === 'ACTIVE')];

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

    this.http.delete(`${this.baseUrl}/${goalId}`).subscribe({
      next: () => {
        this.displayedGoals = this.displayedGoals.filter(g => g.goalId !== goalId);
        if (target?.status === 'ACTIVE') {
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
    if (!goal.progress) {
      return 0;
    }
    const maxGauge = this.getMaxGaugeValue(goal);
    if (maxGauge <= 0) {
      return 0;
    }
    return Math.max(0, (goal.progress.currentValue / maxGauge) * 100);
  }

  getFillPercent(goal: GoalWithProgress): number {
    return Math.min(this.getRawFillPercent(goal), 100);
  }

  isTaskFailed(goal: GoalWithProgress): boolean {
    if (!goal.progress) {
      return false;
    }
    return this.getRawFillPercent(goal) >= 100;
  }

  getZoneColor(goal: GoalWithProgress): string {
    if (!goal.progress) {
      return 'var(--text-secondary)';
    }
    if (this.isTaskFailed(goal)) {
      return 'var(--critical)';
    }
    const p = this.getFillPercent(goal);
    if (p >= 90) {
      return 'var(--critical)';
    }
    if (p >= 75) {
      return 'var(--warning)';
    }
    return 'var(--eco-safe)';
  }

  getStatusLabel(goal: GoalWithProgress): string {
    if (!goal.progress) {
      return 'No Data';
    }
    if (this.isTaskFailed(goal)) {
      return 'TASK FAILED';
    }
    const p = this.getFillPercent(goal);
    if (p >= 90) {
      return 'Critical Breach Warning';
    }
    if (p >= 75) {
      return 'Over Budget \u2014 Warning';
    }
    return 'On Track \u2014 Eco-Safe';
  }

  getArcDashoffset(goal: GoalWithProgress): number {
    const percent = this.getFillPercent(goal);
    return this.arcLength * (1 - percent / 100);
  }

  getProjectedXp(goal: GoalWithProgress): number | null {
    if (!goal.progress) {
      return null;
    }
    if (this.isTaskFailed(goal)) {
      return 0;
    }
    const p = this.getFillPercent(goal);
    const maxXp = goal.rewardPoints;

    if (p < 60) {
      return Math.round(maxXp * 1.15);
    }
    if (p <= 75) {
      return maxXp;
    }
    if (p < 90) {
      return Math.round(maxXp * 0.70);
    }
    return Math.round(maxXp * 0.30);
  }

  getFootprintSaved(goal: GoalWithProgress): number | null {
    if (!goal.progress) {
      return null;
    }
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