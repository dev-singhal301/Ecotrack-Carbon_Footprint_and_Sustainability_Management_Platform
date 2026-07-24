import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
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

  trackingForm = new FormGroup({
    category: new FormControl('', Validators.required),
    amount: new FormControl<number | null>(null, [Validators.required, Validators.min(0.01)]),
    unit: new FormControl({ value: '', disabled: true }),
    activityDate: new FormControl('', Validators.required)
  });

  filters = {
    category: '',
    from: '',
    to: ''
  };

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

  // Center Summary Dynamic State Variables
  totalMonthlyEmission: number = 0;
  hoveredCategory: CategorySummary | null = null;
  categoryList: CategorySummary[] = [];

  // Category Color Palette
  readonly categoryColors: { [key: string]: string } = {
    'FOOD': '#22c55e',          // Soft Emerald
    'WATER': '#0ea5e9',         // Sky Blue
    'FUEL': '#a855f7',          // Soft Purple
    'ELECTRICITY': '#eab308',   // Vibrant Amber
    'WASTE': '#ef4444',          // Coral Red
    'TRANSPORTATION': '#6366f1'  // Indigo Blue
  };

  // =========================================================================
  // 1. Emission Trends (Line Chart) Configuration
  // =========================================================================
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
        pointRadius: 0,
        pointHitRadius: 10
      }
    ]
  };

  public monthlyChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { 
        display: false,
        position: 'top',
        align: 'end'
      },
      tooltip: { mode: 'index', intersect: false }
    },
    scales: {
      x: { 
        title: { display: true, text: 'July 2026', font: { weight: 'bold' } },
        grid: { display: false }
      },
      y: { 
        title: { display: true, text: 'kg CO₂' }, 
        beginAtZero: true,
        grid: { color: 'rgba(0, 0, 0, 0.05)' }
      }
    }
  };

  // =========================================================================
  // 2. Category Footprint (Single-Ring Donut Chart) Configuration
  // =========================================================================
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
    cutout: '76%', // Modern slender ring creating spacious center
    plugins: {
      legend: { 
        display: false // Using custom interactive HTML legend list
      },
      tooltip: {
        enabled: false // Tooltip replaced by dynamic center content overlay
      }
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
          this.historyLogs = data.sort((a, b) => b.entryId - a.entryId);
          this.isLoading = false;
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
        this.historyLogs = [savedEntry, ...this.historyLogs];

        this.trackingForm.reset({
          category: '',
          amount: null,
          unit: '',
          activityDate: this.getTodayDate()
        });

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

  // Handle Chart Hover Events to update Center Summary
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

  // Handle Mouse Leave on Chart Container
  onChartMouseLeave(): void {
    this.hoveredCategory = null;
    this.cdr.detectChanges();
  }

  private updateCharts(): void {
    if (!this.historyLogs || this.historyLogs.length === 0) {
      this.monthlyChartData.labels = [];
      this.monthlyChartData.datasets[0].data = [];
      this.breakdownChartData.datasets[0].data = [0, 0, 0, 0, 0, 0];
      this.totalMonthlyEmission = 0;
      this.categoryList = [];
      return;
    }

    const now = new Date();
    const currentYear = now.getFullYear();
    const currentMonth = now.getMonth();

    const monthLabel = now.toLocaleString('en-US', { month: 'long', year: 'numeric' });
    if (this.monthlyChartOptions.scales?.['x']?.title) {
      this.monthlyChartOptions.scales['x'].title.text = monthLabel;
    }

    // -----------------------------------------------------------------------
    // 1. Line Chart Calculation
    // -----------------------------------------------------------------------
    const dailyTotals: { [dateKey: string]: number } = {};
    
    this.historyLogs.forEach(entry => {
      const date = entry.activityDate;
      const emission = entry.carbonEmission || 0; 
      dailyTotals[date] = (dailyTotals[date] || 0) + emission;
    });

    const sortedDates = Object.keys(dailyTotals).sort((a, b) => new Date(a).getTime() - new Date(b).getTime());
    const dayLabels = sortedDates.map(d => {
      const parts = d.split('-');
      return parts.length === 3 ? parts[2] : d;
    });

    this.monthlyChartData = {
      labels: dayLabels,
      datasets: [
        {
          ...this.monthlyChartData.datasets[0],
          data: sortedDates.map(date => Number(dailyTotals[date].toFixed(2)))
        }
      ]
    };

    // -----------------------------------------------------------------------
    // 2. Single-Ring Donut Chart Calculation (Current Month)
    // -----------------------------------------------------------------------
    const categoryTotals: { [cat: string]: number } = {
      'FOOD': 0,
      'WATER': 0,
      'FUEL': 0,
      'ELECTRICITY': 0,
      'WASTE': 0,
      'TRANSPORTATION': 0
    };

    let monthlySum = 0;

    this.historyLogs.forEach(entry => {
      if (entry.activityDate) {
        const entryDate = new Date(entry.activityDate);
        if (entryDate.getFullYear() === currentYear && entryDate.getMonth() === currentMonth) {
          const cat = entry.category.toUpperCase();
          const val = entry.carbonEmission || 0;
          if (categoryTotals[cat] !== undefined) {
            categoryTotals[cat] += val;
            monthlySum += val;
          }
        }
      }
    });

    this.totalMonthlyEmission = Number(monthlySum.toFixed(2));

    const categories = ['FOOD', 'WATER', 'FUEL', 'ELECTRICITY', 'WASTE', 'TRANSPORTATION'];
    
    // Build Structured Category List for Center Display & UI Legend
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

    // Update Donut Chart Data
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