import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData, ChartType } from 'chart.js';
import { DashboardService } from './dashboard.service';
import { CarbonEntryService } from '../tracking/carbon-entry.service';
import { DashboardSummary, CategoryImpact, GoalSnapshot, XpActivityItem } from './dashboard.model';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { RecommendationService } from '../ai-tips/recommendation.service';
import { GoalInsightService } from '../ai-tips/goal-insight.service';

interface ActiveCategoryLegend {
  name: string;
  color: string;
  textColor: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css']
})
export class Dashboard implements OnInit {

  summary: DashboardSummary | null = null;
  rankedCategoryImpacts: CategoryImpact[] = [];

  isLoading = false;
  dataError: string | null = null;

  public dailyChartType: ChartType = 'bar';
  public last7DaysTotal = 0;
  public hasRecentData = false;
  public activeCategories: ActiveCategoryLegend[] = [];

  public dailyChartData: ChartData<'bar'> = {
    labels: [],
    datasets: []
  };

  public dailyChartOptions: ChartConfiguration['options'] = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: '#1e293b',
        titleFont: { family: 'Inter, sans-serif', size: 12, weight: 'bold' },
        bodyFont: { family: 'Inter, sans-serif', size: 12 },
        padding: 10,
        cornerRadius: 8,
        callbacks: {
          label: (context) => {
            const val = context.raw as number;
            return ` ${context.dataset.label}: ${val.toFixed(1)} kg CO\u2082`;
          }
        }
      }
    },
    scales: {
      x: {
        stacked: true,
        grid: { color: 'rgba(226, 232, 240, 0.6)' },
        ticks: { color: '#64748b', font: { family: 'Inter, sans-serif', size: 11 } }
      },
      y: {
        stacked: true,
        grid: { display: false },
        ticks: { color: '#334155', font: { family: 'Inter, sans-serif', size: 12, weight: 600 } }
      }
    }
  };

  private readonly categoryBgColors: Record<string, string> = {
    FOOD: '#dcfce7',
    WATER: '#e0f2fe',
    FUEL: '#f3e8ff',
    ELECTRICITY: '#fef08a',
    WASTE: '#fee2e2',
    TRANSPORTATION: '#e0e7ff'
  };

  private readonly categoryTextColors: Record<string, string> = {
    FOOD: '#15803d',
    WATER: '#0369a1',
    FUEL: '#6b21a8',
    ELECTRICITY: '#a16207',
    WASTE: '#b91c1c',
    TRANSPORTATION: '#4338ca'
  };

  private readonly categoryLabels: Record<string, string> = {
    TRANSPORTATION: 'Transportation',
    ELECTRICITY: 'Electricity',
    FOOD: 'Food',
    WASTE: 'Waste',
    WATER: 'Water',
    FUEL: 'Fuel'
  };

  private readonly goalTypeUnits: Record<string, string> = {
    REDUCE_CARBON_EMISSIONS: 'kg CO\u2082',
    REDUCE_ELECTRICITY_CONSUMPTION: 'kWh',
    REDUCE_WATER_USAGE: 'litres',
    REDUCE_TRANSPORTATION_EMISSIONS: 'km',
    REDUCE_FUEL_CONSUMPTION: 'litres',
    REDUCE_FOOD_EMISSIONS: 'kg CO\u2082',
    REDUCE_WASTE_GENERATION: 'kg'
  };

  constructor(
    private dashboardService: DashboardService,
    private carbonEntryService: CarbonEntryService,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private recommendationService: RecommendationService,
    private goalInsightService: GoalInsightService
  ) {}

  ngOnInit(): void {
    this.loadData();
    this.recommendationService.ensureLoaded();
    this.goalInsightService.ensureLoaded(); // fires in parallel not chained
  }

  get greetingText(): string {
    return this.summary?.username ? `Hi ${this.summary.username}!` : 'Welcome back!';
  }

  // --- XP & Level Calculations ---
  get currentLevelXpProgress(): number {
    return this.summary?.currentLevelXpProgress || 0;
  }

  get xpForNextLevel(): number {
    return this.summary?.xpForNextLevel || 0;
  }

  // xpForNextLevel of 0 (or less) signals the user has hit the level cap —
  // there's no "next level" to show progress toward, so the template swaps
  // the "X / 0 XP" caption for a MAX LEVEL label instead.
  get isMaxLevel(): boolean {
    return this.xpForNextLevel <= 0;
  }

  get xpProgressPercent(): number {
    if (!this.summary) return 0;
    const target = this.xpForNextLevel;
    if (target <= 0) return 100; // Cap at 100% if user reaches max level
    return Math.min(100, Math.max(0, (this.currentLevelXpProgress / target) * 100));
  }

  get isZeroOrOverBaseline(): boolean {
    return (this.summary?.carbonSaved ?? 0) <= 0;
  }

  // --- Active Goals ---
  get filteredActiveGoals(): GoalSnapshot[] {
    if (!this.summary?.activeGoals) return [];
    return this.summary.activeGoals;
  }

  // --- Recent Activities ---
  // Returns the user's real recent activity only. New accounts genuinely
  // have no history yet, so this returns [] and lets the template's
  // #emptyActivities block ("No recent activity logged yet.") take over —
  // no more placeholder/sample entries standing in for real data.
  get recentActivities(): XpActivityItem[] {
    if (!this.summary?.recentActivities?.length) {
      return [];
    }

    return [...this.summary.recentActivities].sort((a, b) => {
      const timeA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const timeB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      return timeB - timeA; // Descending order: newest entries first
    });
  }

  private formatLocalDate(d: Date): string {
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  loadData(): void {
    this.isLoading = true;
    this.dataError = null;
    this.cdr.detectChanges();

    const today = new Date();
    const windowStart = new Date(today);
    windowStart.setDate(today.getDate() - 7);
    const fromStr = this.formatLocalDate(windowStart);
    const toStr = this.formatLocalDate(today);

    forkJoin({
      summary: this.dashboardService.getSummary().pipe(catchError(() => of(null))),
      history: this.carbonEntryService.getHistory('', fromStr, toStr).pipe(catchError(() => of([])))
    }).subscribe({
      next: ({ summary, history }) => {
        if (summary) {
          this.summary = summary;
          this.rankedCategoryImpacts = [...(summary.categoryImpacts || [])].sort(
            (a, b) => b.totalEmissions - a.totalEmissions
          );
        }

        this.build7DayChartFromLogs(history || []);

        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err: any) => {
        console.error('Failed to load dashboard data:', err);
        this.dataError = err.error?.message || 'Could not load your dashboard. Please try again.';
        this.isLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  private build7DayChartFromLogs(logs: { activityDate: string; category: string; carbonEmission: number }[]): void {
    const today = new Date();
    const anchorDate = new Date(Date.UTC(today.getFullYear(), today.getMonth(), today.getDate()));

    const rawDates: string[] = [];
    const displayLabels: string[] = [];

    for (let i = 6; i >= 0; i--) {
      const d = new Date(anchorDate);
      d.setUTCDate(anchorDate.getUTCDate() - i);

      rawDates.push(d.toISOString().split('T')[0]);
      displayLabels.push(
        i === 0 ? 'Today' : d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' })
      );
    }

    const categories = Object.keys(this.categoryLabels);
    let total7DaySum = 0;
    const categoryTotals: Record<string, number> = {};

    const datasets = categories.map(catKey => {
      const dailyValues = rawDates.map(dateStr => {
        const matchingLogs = logs.filter(log => log.activityDate === dateStr && log.category === catKey);
        return matchingLogs.reduce((sum, log) => sum + (log.carbonEmission || 0), 0);
      });

      const categorySum = dailyValues.reduce((a, b) => a + b, 0);
      categoryTotals[catKey] = categorySum;
      total7DaySum += categorySum;

      return {
        label: this.getCategoryLabel(catKey),
        data: dailyValues,
        backgroundColor: this.categoryBgColors[catKey] || '#e2e8f0',
        borderColor: this.categoryTextColors[catKey] || '#94a3b8',
        borderWidth: 1,
        borderRadius: 4,
        borderSkipped: false
      };
    });

    this.last7DaysTotal = total7DaySum;
    this.hasRecentData = total7DaySum > 0;

    this.activeCategories = categories
      .filter(catKey => (categoryTotals[catKey] || 0) > 0)
      .map(catKey => ({
        name: this.getCategoryLabel(catKey),
        color: this.categoryBgColors[catKey] || '#e2e8f0',
        textColor: this.categoryTextColors[catKey] || '#334155'
      }));

    this.dailyChartData = {
      labels: displayLabels,
      datasets: datasets
    };
  }

  goToTracking(): void {
    this.router.navigate(['/home/tracking']);
  }

  navigateToGoals(): void {
    this.router.navigate(['/home/goals']);
  }

  goToEcoBot(): void {
    this.router.navigate(['/home/ai-tips']);
  }

  getCategoryLabel(category: string): string {
    return this.categoryLabels[category] ?? category;
  }

  getGoalUnit(goal: GoalSnapshot): string {
    return this.goalTypeUnits[goal.goalType] ?? 'kg CO\u2082';
  }

  getGoalFillPercent(goal: GoalSnapshot): number {
    if (goal.targetValue <= 0) return 0;
    return Math.min(100, Math.max(0, (goal.currentValue / goal.targetValue) * 100));
  }
}

export { Dashboard as DashboardComponent };