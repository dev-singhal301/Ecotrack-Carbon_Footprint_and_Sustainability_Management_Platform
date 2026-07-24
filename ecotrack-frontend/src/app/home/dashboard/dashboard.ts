import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData, ChartType } from 'chart.js';
import { DashboardService } from './dashboard.service';
import { CarbonEntryService } from '../tracking/carbon-entry.service';
import { DashboardSummary, CategoryImpact, GoalSnapshot } from './dashboard.model';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

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
            return ` ${context.dataset.label}: ${val.toFixed(1)} kg CO₂`;
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

  private readonly xpPerLevel = 500;

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

  constructor(
    private dashboardService: DashboardService,
    private carbonEntryService: CarbonEntryService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  get greetingText(): string {
    return this.summary?.username ? `Hi ${this.summary.username}!` : 'Welcome back!';
  }

  get xpProgressPercent(): number {
    if (!this.summary) return 0;
    const levelStartXp = (this.summary.userLevel - 1) * this.xpPerLevel;
    const span = this.summary.nextLevelXpTarget - levelStartXp;
    if (span <= 0) return 0;
    const progress = this.summary.currentXp - levelStartXp;
    return Math.min(100, Math.max(0, (progress / span) * 100));
  }

  loadData(): void {
    this.isLoading = true;
    this.dataError = null;
    this.cdr.detectChanges();

    forkJoin({
      summary: this.dashboardService.getSummary().pipe(catchError(() => of(null))),
      history: this.carbonEntryService.getHistory('', '', '').pipe(catchError(() => of([])))
    }).subscribe({
      next: ({ summary, history }) => {
        if (summary) {
          this.summary = summary;
          this.rankedCategoryImpacts = [...(summary.categoryImpacts || [])].sort(
            (a, b) => b.totalEmissions - a.totalEmissions
          );
        }

        // Use direct tracking entries, fallback to summary logs if empty
        const rawLogs = (history && history.length > 0) 
          ? history 
          : ((summary as any)?.recentLogs || (summary as any)?.dailyEmissions || []);

        this.build7DayChartFromLogs(rawLogs);

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

  private normalizeDateString(dateVal: any): string {
    if (!dateVal) return '';
    if (typeof dateVal === 'string') {
      return dateVal.split('T')[0];
    }
    const d = new Date(dateVal);
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private build7DayChartFromLogs(logs: any[]): void {
    if (!logs || logs.length === 0) {
      this.hasRecentData = false;
      this.last7DaysTotal = 0;
      return;
    }

    const getLogEmissions = (log: any): number => {
      const val = log.carbonEmission ?? log.calculatedEmission ?? log.emissions ?? log.co2Amount ?? log.carbonFootprint ?? log.totalEmissions ?? 0;
      if (typeof val === 'string') {
        return parseFloat(val.replace(/[^0-9.]/g, '')) || 0;
      }
      return Number(val) || 0;
    };

    const getLogCategory = (log: any): string => {
      const cat = log.category || log.categoryName || log.type || '';
      return cat.toUpperCase();
    };

    // Pick latest entry date to focus X-axis dynamically (supporting activityDate from CarbonEntryResponse)
    const logDates = logs
      .map(log => this.normalizeDateString(log.activityDate || log.date || log.logDate || log.createdAt || log.timestamp))
      .filter(d => d.length === 10)
      .sort();

    const latestLogDateStr = logDates.length > 0 ? logDates[logDates.length - 1] : new Date().toISOString().split('T')[0];
    
    const [anchorY, anchorM, anchorD] = latestLogDateStr.split('-').map(Number);
    const anchorDate = new Date(Date.UTC(anchorY, anchorM - 1, anchorD));

    const rawDates: string[] = [];
    const displayLabels: string[] = [];

    for (let i = 6; i >= 0; i--) {
      const d = new Date(anchorDate);
      d.setUTCDate(anchorDate.getUTCDate() - i);

      const isoDate = d.toISOString().split('T')[0];
      rawDates.push(isoDate);

      const label = i === 0 ? 'Latest Entry' : d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' });
      displayLabels.push(label);
    }

    const categories = Object.keys(this.categoryLabels);
    let total7DaySum = 0;
    const categoryTotals: Record<string, number> = {};

    const datasets = categories.map(catKey => {
      const dailyValues = rawDates.map(dateStr => {
        const matchingLogs = logs.filter(log => {
          const lDate = this.normalizeDateString(log.activityDate || log.date || log.logDate || log.createdAt || log.timestamp);
          const lCat = getLogCategory(log);
          return lDate === dateStr && lCat === catKey;
        });

        return matchingLogs.reduce((sum, log) => sum + getLogEmissions(log), 0);
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

  getCategoryLabel(category: string): string {
    return this.categoryLabels[category] ?? category;
  }

  getGoalFillPercent(goal: GoalSnapshot): number {
    if (goal.targetValue <= 0) return 0;
    return Math.min(100, Math.max(0, (goal.currentValue / goal.targetValue) * 100));
  }

  getGoalStatusLabel(goal: GoalSnapshot): string {
    if (goal.currentValue > goal.targetValue) return 'Over Budget';
    if (this.getGoalFillPercent(goal) >= 85) return 'Approaching Limit';
    return 'On Track';
  }

  getGoalStatusColor(goal: GoalSnapshot): string {
    if (goal.currentValue > goal.targetValue) return 'var(--eco-red)';
    if (this.getGoalFillPercent(goal) >= 85) return 'var(--eco-warning)';
    return 'var(--eco-green)';
  }
}

export { Dashboard as DashboardComponent };