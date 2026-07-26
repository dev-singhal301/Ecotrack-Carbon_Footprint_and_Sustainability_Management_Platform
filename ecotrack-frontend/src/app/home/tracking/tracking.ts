import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormGroup, FormControl, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartOptions, Chart, registerables } from 'chart.js';
import { CarbonEntryService, CarbonEntryRequest, CarbonEntryResponse } from './carbon-entry.service';

Chart.register(...registerables);

export interface CategorySummary {
  name: string;
  value: number;
  percentage: number;
  color: string;
}

@Component({
  selector: 'app-tracking',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    BaseChartDirective
  ],
  templateUrl: './tracking.html',
  styleUrls: ['./tracking.css']
})
export class TrackingComponent implements OnInit {

  readonly maxActivityDate = this.getTodayDate();

  // Robust future date validator comparing dates normalized to midnight
  private notFutureDateValidator = (control: AbstractControl): ValidationErrors | null => {
    if (!control.value) return null;
    const selected = new Date(control.value);
    const today = new Date();
    today.setHours(23, 59, 59, 999); // Allow up to end of today
    return selected > today ? { futureDate: true } : null;
  };

  trackingForm = new FormGroup({
    category: new FormControl('', Validators.required),
    amount: new FormControl<number | null>(null, [Validators.required, Validators.min(0.01)]),
    unit: new FormControl({ value: '', disabled: true }),
    activityDate: new FormControl('', [Validators.required, this.notFutureDateValidator])
  });

  filters = {
    category: '',
    from: '',
    to: ''
  };

  // Month selector state for charts (Format: 'YYYY-MM')
  selectedChartMonth: string = this.getCurrentYearMonth();
  availableMonths: { label: string; value: string }[] = [];

  expandedSection: string | null = null;

  private unitMapping: { [key: string]: { unit: string } } = {
    'TRANSPORTATION': { unit: 'km' },
    'ELECTRICITY': { unit: 'kWh' },
    'FOOD': { unit: 'kg' },
    'WASTE': { unit: 'kg' },
    'WATER': { unit: 'litres' },
    'FUEL': { unit: 'litres' }
  };

  historyLogs: CarbonEntryResponse[] = [];
  isLoading: boolean = false;
  removingIds = new Set<number>();

  totalMonthlyEmission: number = 0;
  hoveredCategory: CategorySummary | null = null;
  categoryList: CategorySummary[] = [];

  readonly categoryColors: { [key: string]: string } = {
    'FOOD': '#22c55e',
    'WATER': '#0ea5e9',
    'FUEL': '#a855f7',
    'ELECTRICITY': '#eab308',
    'WASTE': '#ef4444',
    'TRANSPORTATION': '#6366f1'
  };

  public monthlyChartType: ChartConfiguration<'line'>['type'] = 'line';

  public monthlyChartData: ChartConfiguration<'line'>['data'] = {
    labels: [],
    datasets: [
      {
        data: [],
        label: 'Daily Footprint (kg CO₂)',
        fill: true,
        borderColor: '#0fac34',
        borderWidth: 2,
        cubicInterpolationMode: 'monotone',
        tension: 0.4,
        backgroundColor: (context) => {
          const chart = context.chart;
          const { ctx, chartArea } = chart;
          if (!chartArea) return 'rgba(15, 172, 52, 0.1)';

          const gradient = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
          gradient.addColorStop(0, 'rgba(15, 172, 52, 0.25)');
          gradient.addColorStop(1, 'rgba(15, 172, 52, 0.01)');
          return gradient;
        },
        pointBackgroundColor: '#0fac34',
        pointBorderColor: '#ffffff',
        pointBorderWidth: 2,
        pointHoverBackgroundColor: '#0a7d25',
        pointHoverRadius: 5,
        pointRadius: 3,
        pointHitRadius: 10
      }
    ]
  };

  public monthlyChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: { mode: 'index', intersect: false }
    },
    scales: {
      x: {
        title: { display: true, text: 'Day of Month', font: { weight: 'bold' } },
        grid: { display: false }
      },
      y: {
        title: { display: true, text: 'kg CO₂' },
        beginAtZero: true,
        grid: { color: 'rgba(0, 0, 0, 0.05)' }
      }
    }
  };

  public breakdownChartType: ChartConfiguration<'doughnut'>['type'] = 'doughnut';

  public breakdownChartData: ChartConfiguration<'doughnut'>['data'] = {
    labels: ['FOOD', 'WATER', 'FUEL', 'ELECTRICITY', 'WASTE', 'TRANSPORTATION'],
    datasets: [
      {
        data: [0, 0, 0, 0, 0, 0],
        backgroundColor: [
          '#22c55e',
          '#0ea5e9',
          '#a855f7',
          '#eab308',
          '#ef4444',
          '#6366f1'
        ],
        borderWidth: 2,
        borderColor: '#ffffff',
        hoverOffset: 10
      }
    ]
  };

  public breakdownChartOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '76%',
    plugins: {
      legend: { display: false },
      tooltip: { enabled: false }
    },
    onHover: (event, activeElements) => {
      this.onChartHover(activeElements);
    }
  };

  constructor(
    private carbonService: CarbonEntryService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.trackingForm.patchValue({ activityDate: this.getTodayDate() });
    this.loadHistory();
  }

  private getTodayDate(): string {
    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  private getCurrentYearMonth(): string {
    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    return `${yyyy}-${mm}`;
  }

  onCategoryChange(): void {
    const category = this.trackingForm.get('category')?.value ?? '';
    const mapping = this.unitMapping[category];
    this.trackingForm.patchValue({ unit: mapping ? mapping.unit : '' });
  }

  toggleExpand(section: string): void {
    this.expandedSection = this.expandedSection === section ? null : section;

    const isTrendExpanded = this.expandedSection === 'trend';
    if (this.monthlyChartOptions.plugins?.legend) {
      this.monthlyChartOptions.plugins.legend.display = isTrendExpanded;
    }

    this.monthlyChartData = { ...this.monthlyChartData };
    this.breakdownChartData = { ...this.breakdownChartData };
    this.cdr.detectChanges();
  }

  loadHistory(): void {
    this.isLoading = true;

    this.carbonService.getHistory(this.filters.category, this.filters.from, this.filters.to)
      .subscribe({
        next: (data) => {
          this.historyLogs = data.sort((a, b) => {
            if (a.activityDate !== b.activityDate) {
              return b.activityDate.localeCompare(a.activityDate);
            }
            return b.entryId - a.entryId;
          });
          this.isLoading = false;
          this.populateAvailableMonths();
          this.updateCharts();
          this.cdr.detectChanges();
        },
        error: (err) => {
          console.error('Failed to load carbon entry records from backend:', err);
          this.isLoading = false;
          this.cdr.detectChanges();
        }
      });
  }

  private populateAvailableMonths(): void {
    const monthMap = new Map<string, string>();
    
    // Always include current month
    const now = new Date();
    const currentKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    const currentLabel = now.toLocaleString('en-US', { month: 'long', year: 'numeric' });
    monthMap.set(currentKey, currentLabel);

    // Extract unique months from history logs
    this.historyLogs.forEach(entry => {
      if (entry.activityDate) {
        const [year, month] = entry.activityDate.split('-');
        const key = `${year}-${month}`;
        if (!monthMap.has(key)) {
          const d = new Date(Number(year), Number(month) - 1, 1);
          const label = d.toLocaleString('en-US', { month: 'long', year: 'numeric' });
          monthMap.set(key, label);
        }
      }
    });

    this.availableMonths = Array.from(monthMap.entries()).map(([value, label]) => ({
      value,
      label
    })).sort((a, b) => b.value.localeCompare(a.value));
  }

  onMonthFilterChange(): void {
    this.updateCharts();
    this.cdr.detectChanges();
  }

  onLogSubmit(): void {
    if (this.trackingForm.invalid) {
      this.trackingForm.markAllAsTouched();
      return;
    }

    const raw = this.trackingForm.getRawValue();
    const payload: CarbonEntryRequest = {
      category: raw.category ?? '',
      amount: Number(raw.amount),
      unit: raw.unit ?? '',
      activityDate: raw.activityDate ?? ''
    };

    this.carbonService.createEntry(payload).subscribe({
      next: (savedEntry: CarbonEntryResponse) => {
        this.historyLogs = [savedEntry, ...this.historyLogs].sort((a, b) => {
          if (a.activityDate !== b.activityDate) {
            return b.activityDate.localeCompare(a.activityDate);
          }
          return b.entryId - a.entryId;
        });

        this.trackingForm.reset({
          category: '',
          amount: null,
          unit: '',
          activityDate: this.getTodayDate()
        });

        this.populateAvailableMonths();
        this.updateCharts();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Submission error context:', err);
        alert(err.error?.message || 'Error occurred while saving your log entry to the server.');
      }
    });
  }

  onDeleteEntry(id: number): void {
    if (!confirm('Are you sure you want to delete this recorded log permanently?')) {
      return;
    }

    this.carbonService.deleteEntry(id).subscribe({
      next: () => {
        this.removingIds.add(id);
        this.cdr.detectChanges();

        setTimeout(() => {
          this.historyLogs = this.historyLogs.filter(item => item.entryId !== id);
          this.removingIds.delete(id);
          this.populateAvailableMonths();
          this.updateCharts();
          this.cdr.detectChanges();
        }, 280);
      },
      error: (err) => {
        alert(err.error?.message || 'Failed to remove log record from database.');
      }
    });
  }

  clearFilters(): void {
    this.filters = { category: '', from: '', to: '' };
    this.loadHistory();
    this.cdr.detectChanges();
  }

  private onChartHover(activeElements: any[]): void {
    if (activeElements && activeElements.length > 0) {
      const index = activeElements[0].index;
      if (this.categoryList[index]) {
        this.hoveredCategory = this.categoryList[index];
        this.cdr.detectChanges();
      }
    } else if (this.hoveredCategory !== null) {
      this.hoveredCategory = null;
      this.cdr.detectChanges();
    }
  }

  onChartMouseLeave(): void {
    this.hoveredCategory = null;
    this.cdr.detectChanges();
  }

  private updateCharts(): void {
    const [targetYearStr, targetMonthStr] = this.selectedChartMonth.split('-');
    const targetYear = Number(targetYearStr);
    const targetMonth = Number(targetMonthStr) - 1; // 0-indexed month

    const selectedMonthObj = new Date(targetYear, targetMonth, 1);
    const monthLabel = selectedMonthObj.toLocaleString('en-US', { month: 'long', year: 'numeric' });

    // Update scale label cleanly
    this.monthlyChartOptions = {
      ...this.monthlyChartOptions,
      scales: {
        ...this.monthlyChartOptions.scales,
        x: {
          ...this.monthlyChartOptions.scales?.['x'],
          title: { display: true, text: monthLabel, font: { weight: 'bold' } }
        }
      }
    };

    // Filter logs for selected month
    const filteredLogs = this.historyLogs.filter(entry => {
      if (!entry.activityDate) return false;
      const [y, m] = entry.activityDate.split('-').map(Number);
      return y === targetYear && (m - 1) === targetMonth;
    });

    // 1. Build Daily Footprint Line Chart
    const dailyTotals: { [day: number]: number } = {};
    const daysInMonth = new Date(targetYear, targetMonth + 1, 0).getDate();

    // Map existing records to day numbers
    filteredLogs.forEach(entry => {
      const day = Number(entry.activityDate.split('-')[2]);
      const emission = entry.carbonEmission || 0;
      dailyTotals[day] = (dailyTotals[day] || 0) + emission;
    });

    const sortedDays = Object.keys(dailyTotals).map(Number).sort((a, b) => a - b);
    
    this.monthlyChartData = {
      labels: sortedDays.map(d => `${d}`),
      datasets: [
        {
          ...this.monthlyChartData.datasets[0],
          data: sortedDays.map(day => Number(dailyTotals[day].toFixed(2)))
        }
      ]
    };

    // 2. Build Category Footprint Breakdown
    const categoryTotals: { [cat: string]: number } = {
      'FOOD': 0,
      'WATER': 0,
      'FUEL': 0,
      'ELECTRICITY': 0,
      'WASTE': 0,
      'TRANSPORTATION': 0
    };

    let monthlySum = 0;

    filteredLogs.forEach(entry => {
      const cat = entry.category.toUpperCase();
      const val = entry.carbonEmission || 0;
      if (categoryTotals[cat] !== undefined) {
        categoryTotals[cat] += val;
        monthlySum += val;
      }
    });

    this.totalMonthlyEmission = Number(monthlySum.toFixed(2));

    const categories = ['FOOD', 'WATER', 'FUEL', 'ELECTRICITY', 'WASTE', 'TRANSPORTATION'];

    this.categoryList = categories.map(cat => {
      const val = Number(categoryTotals[cat].toFixed(2));
      const pct = this.totalMonthlyEmission > 0
        ? Number(((val / this.totalMonthlyEmission) * 100).toFixed(1))
        : 0;

      return {
        name: cat,
        value: val,
        percentage: pct,
        color: this.categoryColors[cat]
      };
    });

    this.breakdownChartData = {
      labels: categories,
      datasets: [
        {
          data: this.categoryList.map(c => c.value),
          backgroundColor: categories.map(cat => this.categoryColors[cat]),
          borderWidth: 2,
          borderColor: '#ffffff',
          hoverOffset: 10
        }
      ]
    };
  }
}