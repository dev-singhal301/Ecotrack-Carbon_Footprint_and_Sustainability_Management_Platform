import { Component, OnInit, signal, computed, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
// provideCharts(withDefaultRegisterables()) is already registered globally
// in app.config.ts — only the directive is needed here.
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions, Plugin } from 'chart.js';
import { AdminService } from '../admin.service';
import { AdminUser } from '../users/users.model';
import { Challenge } from '../../../types/challenge';
import {
  AdminDashboardSummary,
  AdminParticipation,
  DashboardDateRange,
  TrendBucket,
} from './dashboard.model';

// Semantic colors mirrored from the --admin-* CSS variables in
// home-admin.css. Chart.js configs are plain JS objects rendered onto a
// <canvas>, so they can't read CSS custom properties directly — these
// hex values must be kept in sync with home-admin.css by hand.
// Amber (--admin-warning, #a16207) is used for in-progress/warning
// accents in the CSS directly (e.g. the Needs Attention icons) rather
// than in a chart dataset here.
const COLOR_INFO = '#2851a3';      // blue — info / upcoming / activity
const COLOR_SUCCESS = '#1e7a3d';   // green — success / completed / positive
const COLOR_DANGER = '#b91c1c';    // red — cancelled / disabled / negative

// Chart.js's own plugin API (part of the already-installed chart.js
// package — not a new dependency, e.g. not chartjs-plugin-datalabels).
// Draws the resolved completion rate as text above each point, so the
// trend is readable at a glance without hovering.
const percentPointLabelPlugin: Plugin<'line'> = {
  id: 'percentPointLabel',
  afterDatasetsDraw(chart) {
    const { ctx } = chart;
    const meta = chart.getDatasetMeta(0);
    const dataset = chart.data.datasets[0];
    if (!dataset) return;

    meta.data.forEach((point, index) => {
      const value = dataset.data[index] as number | null;
      if (value === null || value === undefined) return;
      ctx.save();
      ctx.font = "600 11px 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif";
      ctx.fillStyle = COLOR_SUCCESS;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'bottom';
      ctx.fillText(`${value}%`, point.x, point.y - 8);
      ctx.restore();
    });
  },
};

/**
 * Admin Insights Dashboard.
 *
 * Two conceptual zones, per the redesign brief:
 *  - CURRENT OVERVIEW: current-state snapshot values, never affected by
 *    the date filter (Total Users, Active Users, Active Challenges, etc).
 *  - HISTORICAL sections: trend charts that DO respect the date filter
 *    (Registration Trend, Challenge Activity, Participation Activity,
 *    Resolved Completion Rate Trend).
 *
 * Data comes from four independent sources, each with its own
 * loading/error signal, so one failing endpoint doesn't take down
 * sections that depend on the others:
 *  - summary       -> GET /api/admin/dashboard-summary (existing, untouched)
 *  - users         -> GET /api/admin/users (existing; now also used for
 *                      the Registration Trend chart's createdAt values)
 *  - challenges    -> GET /api/admin/challenges (existing)
 *  - participations -> GET /api/admin/participations (new, minimal
 *                      admin-only endpoint added for this redesign)
 *
 * All date-range filtering/bucketing happens client-side over the fully
 * fetched lists, matching the existing admin-portal convention (see
 * Challenges' filteredChallenges) rather than adding server-side
 * date-range query parameters.
 *
 * Uses signals for state written inside HttpClient subscribe callbacks —
 * this app is zoneless, so a plain field assignment inside .subscribe()
 * would not trigger a re-render (same reasoning as the rest of the admin
 * portal).
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, BaseChartDirective],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css'],
})
export class Dashboard implements OnInit {
  // ── Raw data + per-section load state ──

  summary = signal<AdminDashboardSummary | null>(null);
  summaryLoading = signal(false);
  summaryError = signal<string | null>(null);

  users = signal<AdminUser[]>([]);
  usersLoading = signal(false);
  usersError = signal<string | null>(null);

  challenges = signal<Challenge[]>([]);
  challengesLoading = signal(false);
  challengesError = signal<string | null>(null);

  participations = signal<AdminParticipation[]>([]);
  participationsLoading = signal(false);
  participationsError = signal<string | null>(null);

  // ── Date filter — drives historical/trend sections only ──

  dateRange = signal<DashboardDateRange>('30d');
  customFrom = signal<string>('');
  customTo = signal<string>('');

  readonly rangeOptions: { value: DashboardDateRange; label: string }[] = [
    { value: '7d', label: '7 Days' },
    { value: '30d', label: '30 Days' },
    { value: '90d', label: '90 Days' },
    { value: 'year', label: 'This Year' },
    { value: 'custom', label: 'Custom' },
  ];

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadSummary();
  }

  // Entry point called by HomeAdmin's router-outlet (activate) handler —
  // name kept as loadSummary() for compatibility with that existing
  // dispatch logic. Triggers all four independent data loads.
  loadSummary(): void {
    this.loadUserSummary();
    this.loadUsers();
    this.loadChallenges();
    this.loadParticipations();
  }

  loadUserSummary(): void {
    this.summaryLoading.set(true);
    this.summaryError.set(null);

    this.adminService.getDashboardSummary().subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.summaryLoading.set(false);
      },
      error: (err) => {
        console.error('Failed to load admin dashboard summary:', err);
        this.summaryError.set(err.error?.message || 'Could not load user statistics. Please try again.');
        this.summaryLoading.set(false);
      },
    });
  }

  loadUsers(): void {
    this.usersLoading.set(true);
    this.usersError.set(null);

    this.adminService.getUsers().subscribe({
      next: (users) => {
        this.users.set(users);
        this.usersLoading.set(false);
      },
      error: (err) => {
        console.error('Failed to load users for registration trend:', err);
        this.usersError.set(err.error?.message || 'Could not load user registration data.');
        this.usersLoading.set(false);
      },
    });
  }

  loadChallenges(): void {
    this.challengesLoading.set(true);
    this.challengesError.set(null);

    this.adminService.getChallenges().subscribe({
      next: (challenges) => {
        this.challenges.set(challenges);
        this.challengesLoading.set(false);
      },
      error: (err) => {
        console.error('Failed to load challenges for dashboard:', err);
        this.challengesError.set(err.error?.message || 'Could not load challenge data.');
        this.challengesLoading.set(false);
      },
    });
  }

  loadParticipations(): void {
    this.participationsLoading.set(true);
    this.participationsError.set(null);

    this.adminService.getParticipations().subscribe({
      next: (participations) => {
        this.participations.set(participations);
        this.participationsLoading.set(false);
      },
      error: (err) => {
        console.error('Failed to load participations for dashboard:', err);
        this.participationsError.set(err.error?.message || 'Could not load participation data.');
        this.participationsLoading.set(false);
      },
    });
  }

  setDateRange(range: DashboardDateRange): void {
    this.dateRange.set(range);
  }

  // Human-readable label for whichever range is currently selected, shown
  // next to each trend chart's title so it's unambiguous which charts are
  // (and aren't) affected by the filter above.
  selectedRangeLabel = computed(() => {
    const opt = this.rangeOptions.find((o) => o.value === this.dateRange());
    return opt ? opt.label : '';
  });

  // ── Maximize / expand overlay ──
  // A modal that re-renders the same chart data larger. Originally
  // scoped to the three full-width charts only; Challenge Activity
  // (in the two-column Challenges row) was added on request even though
  // its card isn't full-width — the maximize affordance is still useful
  // there, it just doesn't change that row's own layout.

  readonly expandableCharts: { key: 'registration' | 'participation' | 'completion' | 'challengeActivity'; title: string }[] = [
    { key: 'registration', title: 'User Registration Trend' },
    { key: 'participation', title: 'Participation Activity' },
    { key: 'completion', title: 'Challenge Completion Rate Trend' },
    { key: 'challengeActivity', title: 'Challenge Activity' },
  ];

  expandedChart = signal<'registration' | 'participation' | 'completion' | 'challengeActivity' | null>(null);

  toggleChartExpand(key: 'registration' | 'participation' | 'completion' | 'challengeActivity'): void {
    this.expandedChart.set(this.expandedChart() === key ? null : key);
  }

  closeExpandedChart(): void {
    this.expandedChart.set(null);
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.expandedChart() !== null) {
      this.closeExpandedChart();
    }
  }

  expandedChartTitle = computed(() => {
    const key = this.expandedChart();
    return this.expandableCharts.find((c) => c.key === key)?.title ?? '';
  });

  // ── CURRENT OVERVIEW (never affected by dateRange) ──

  activeUsersPct = computed<number | null>(() => {
    const s = this.summary();
    if (!s || s.totalUsers === 0) return null;
    return Math.round((s.activeUsers / s.totalUsers) * 1000) / 10;
  });

  activeChallengesCount = computed(() => this.challenges().filter((c) => c.status === 'ACTIVE').length);

  upcomingChallengesCount = computed(() => this.challenges().filter((c) => c.status === 'UPCOMING').length);

  // JOINED and IN_PROGRESS are both "currently participating, not yet
  // resolved" from an admin's point of view — combined into one
  // current-state bucket per the redesign brief rather than split.
  inProgressParticipationsCount = computed(
    () => this.participations().filter((p) => p.status === 'JOINED' || p.status === 'IN_PROGRESS').length
  );

  // Lifetime (all-time) figure — deliberately NOT date-filtered, so it
  // reads as a current snapshot. The filtered version lives in
  // completionRateTrendData below and is labelled separately so the two
  // don't look contradictory side by side.
  resolvedCompletionRate = computed<number | null>(() => {
    const all = this.participations();
    const completed = all.filter((p) => p.status === 'COMPLETED').length;
    const expired = all.filter((p) => p.status === 'EXPIRED').length;
    const denom = completed + expired;
    return denom === 0 ? null : Math.round((completed / denom) * 1000) / 10;
  });

  // Lifetime count — CANCELLED participations have no resolution
  // timestamp anywhere in the schema (UserChallengeService only sets
  // completedAt for COMPLETED/EXPIRED; the cascade-cancel path sets
  // status only). There is no field to bucket a cancellation by date
  // without inventing one, so this stays a lifetime figure rather than
  // a line in Participation Activity.
  cancelledParticipationsCount = computed(
    () => this.participations().filter((p) => p.status === 'CANCELLED').length
  );

  // ── KPI trend context ──
  //
  // Added only where a real historical comparison is derivable from data
  // we already have — not applied to every KPI:
  //  - Active Users: the schema has no history of status changes, only
  //    the current enabled/disabled flag. No way to know who was active
  //    7 days ago without inventing it.
  //  - Active Challenges: would require re-deriving UPCOMING/ACTIVE/
  //    ENDED/CANCELLED as of a past date — but ChallengeResponse.
  //    deriveStatus() is explicitly meant to be the single source of
  //    that logic; duplicating it client-side risks the two drifting
  //    out of sync, so this is intentionally skipped.
  //  - In-Progress Participations: CANCELLED participations have no
  //    timestamp (see cancelledParticipationsCount above), so a past
  //    in-progress count can't be reconstructed reliably.

  // Total Users: newUsersThisWeek (already a rolling-7-day backend
  // count, confirmed via UserRepository.countByCreatedAtAfter) as a
  // % of the base that existed before this week — reuses existing
  // summary fields, no extra fetch or computation.
  totalUsersGrowthPct = computed<number | null>(() => {
    const s = this.summary();
    if (!s) return null;
    const baseBeforeThisWeek = s.totalUsers - s.newUsersThisWeek;
    if (baseBeforeThisWeek <= 0) return null;
    return Math.round((s.newUsersThisWeek / baseBeforeThisWeek) * 1000) / 10;
  });

  // New Users This Week vs. the prior rolling 7-day window — both counted
  // client-side from the same `users` list using the same rolling-window
  // definition the backend uses, so it's an apples-to-apples comparison.
  newUsersTrendDelta = computed<number | null>(() => {
    const list = this.users();
    if (list.length === 0) return null;
    const now = new Date();
    const sevenDaysAgo = new Date(now.getTime() - 7 * 86400000);
    const fourteenDaysAgo = new Date(now.getTime() - 14 * 86400000);
    const thisWeek = list.filter((u) => new Date(u.createdAt) > sevenDaysAgo).length;
    const priorWeek = list.filter((u) => {
      const d = new Date(u.createdAt);
      return d > fourteenDaysAgo && d <= sevenDaysAgo;
    }).length;
    return thisWeek - priorWeek;
  });

  // Resolved Completion Rate vs. the same lifetime calculation evaluated
  // only over participations that had already resolved 7 days ago — a
  // real historical view of the same metric (completedAt is a genuine
  // timestamp we have), not an invented figure.
  resolvedCompletionRateTrendDelta = computed<number | null>(() => {
    const current = this.resolvedCompletionRate();
    if (current === null) return null;

    const sevenDaysAgo = new Date(Date.now() - 7 * 86400000);
    const priorRecords = this.participations().filter(
      (p) => p.completedAt && new Date(p.completedAt) <= sevenDaysAgo
    );
    const completed = priorRecords.filter((p) => p.status === 'COMPLETED').length;
    const expired = priorRecords.filter((p) => p.status === 'EXPIRED').length;
    const denom = completed + expired;
    if (denom === 0) return null;

    const priorRate = Math.round((completed / denom) * 1000) / 10;
    return Math.round((current - priorRate) * 10) / 10;
  });

  // ── Date-range bucketing (historical sections only) ──

  private getRangeBounds(): { start: Date; end: Date; granularity: 'day' | 'week' } {
    const now = new Date();
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999);
    const range = this.dateRange();

    if (range === '7d') {
      const start = new Date(end);
      start.setDate(start.getDate() - 6);
      start.setHours(0, 0, 0, 0);
      return { start, end, granularity: 'day' };
    }
    if (range === '30d') {
      const start = new Date(end);
      start.setDate(start.getDate() - 29);
      start.setHours(0, 0, 0, 0);
      return { start, end, granularity: 'day' };
    }
    if (range === '90d') {
      const start = new Date(end);
      start.setDate(start.getDate() - 89);
      start.setHours(0, 0, 0, 0);
      return { start, end, granularity: 'week' };
    }
    if (range === 'year') {
      const start = new Date(now.getFullYear(), 0, 1);
      return { start, end, granularity: 'week' };
    }

    // Custom — falls back to the last 30 days until both dates are picked.
    const from = this.customFrom() ? new Date(`${this.customFrom()}T00:00:00`) : (() => {
      const d = new Date(end);
      d.setDate(d.getDate() - 29);
      d.setHours(0, 0, 0, 0);
      return d;
    })();
    const to = this.customTo() ? new Date(`${this.customTo()}T23:59:59`) : end;
    const spanDays = Math.max(1, Math.round((to.getTime() - from.getTime()) / 86400000));
    // Day buckets stay readable up to ~45 points on the x-axis; beyond
    // that, switch to weekly buckets.
    return { start: from, end: to, granularity: spanDays <= 45 ? 'day' : 'week' };
  }

  private formatDayLabel(d: Date): string {
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }

  private formatWeekLabel(start: Date, end: Date): string {
    const s = start.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    const e = end.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    return `${s}–${e}`;
  }

  private buildBuckets(start: Date, end: Date, granularity: 'day' | 'week'): TrendBucket[] {
    const buckets: TrendBucket[] = [];

    if (granularity === 'day') {
      const cursor = new Date(start);
      while (cursor <= end) {
        const bucketStart = new Date(cursor);
        bucketStart.setHours(0, 0, 0, 0);
        const bucketEnd = new Date(cursor);
        bucketEnd.setHours(23, 59, 59, 999);
        buckets.push({ label: this.formatDayLabel(bucketStart), bucketStart, bucketEnd });
        cursor.setDate(cursor.getDate() + 1);
      }
    } else {
      const cursor = new Date(start);
      while (cursor <= end) {
        const bucketStart = new Date(cursor);
        bucketStart.setHours(0, 0, 0, 0);
        const bucketEnd = new Date(cursor);
        bucketEnd.setDate(bucketEnd.getDate() + 6);
        bucketEnd.setHours(23, 59, 59, 999);
        const clippedEnd = bucketEnd > end ? end : bucketEnd;
        buckets.push({ label: this.formatWeekLabel(bucketStart, clippedEnd), bucketStart, bucketEnd: clippedEnd });
        cursor.setDate(cursor.getDate() + 7);
      }
    }
    return buckets;
  }

  buckets = computed<TrendBucket[]>(() => {
    const { start, end, granularity } = this.getRangeBounds();
    return this.buildBuckets(start, end, granularity);
  });

  // Shared rule for charts that get noisy on daily buckets once a range
  // gets long: if the selected span is 28 days (4 weeks) or more, floor
  // to weekly buckets regardless of what getRangeBounds' own day/week
  // split would otherwise pick; below that, use its natural granularity.
  // Span-based (not tied to a specific preset) so Custom ranges get the
  // same treatment as the fixed presets. Applied to Challenge Activity,
  // Participation Activity, and Resolved Completion Rate Trend — not to
  // Registration Trend, which keeps using `buckets` above as before.
  private floorToWeekAboveDays(minDays: number): TrendBucket[] {
    const { start, end, granularity } = this.getRangeBounds();
    const spanDays = Math.round((end.getTime() - start.getTime()) / 86400000) + 1;
    return this.buildBuckets(start, end, spanDays >= minDays ? 'week' : granularity);
  }

  activityBuckets = computed<TrendBucket[]>(() => this.floorToWeekAboveDays(28));

  completionRateBuckets = computed<TrendBucket[]>(() => this.floorToWeekAboveDays(28));

  private countInBuckets(buckets: TrendBucket[], dates: (Date | null)[]): number[] {
    return buckets.map((b) => dates.filter((d) => d && d >= b.bucketStart && d <= b.bucketEnd).length);
  }

  private inBucket(d: Date, b: TrendBucket): boolean {
    return d >= b.bucketStart && d <= b.bucketEnd;
  }

  // ── HISTORICAL / TREND charts (all respect dateRange) ──

  registrationTrendData = computed<ChartData<'line'>>(() => {
    const buckets = this.buckets();
    const dates = this.users().map((u) => (u.createdAt ? new Date(u.createdAt) : null));
    return {
      labels: buckets.map((b) => b.label),
      datasets: [
        {
          label: 'New Users',
          data: this.countInBuckets(buckets, dates),
          borderColor: COLOR_INFO,
          backgroundColor: 'rgba(40, 81, 163, 0.10)',
          fill: true,
          tension: 0.3,
          pointRadius: 3,
          pointHoverRadius: 5,
        },
      ],
    };
  });

  challengeActivityData = computed<ChartData<'bar'>>(() => {
    const buckets = this.activityBuckets();
    const createdDates = this.challenges().map((c) => new Date(c.createdAt));
    const cancelledDates = this.challenges()
      .filter((c) => c.cancelled && c.cancelledAt)
      .map((c) => new Date(c.cancelledAt as string));
    return {
      labels: buckets.map((b) => b.label),
      datasets: [
        {
          label: 'Created',
          data: this.countInBuckets(buckets, createdDates),
          backgroundColor: COLOR_INFO,
          borderRadius: 4,
          maxBarThickness: 28,
        },
        {
          label: 'Cancelled',
          data: this.countInBuckets(buckets, cancelledDates),
          backgroundColor: COLOR_DANGER,
          borderRadius: 4,
          maxBarThickness: 28,
        },
      ],
    };
  });

  participationActivityData = computed<ChartData<'line'>>(() => {
    const buckets = this.activityBuckets();
    const started = this.participations().map((p) => new Date(p.joinedAt));
    const completed = this.participations()
      .filter((p) => p.status === 'COMPLETED' && p.completedAt)
      .map((p) => new Date(p.completedAt as string));
    const expired = this.participations()
      .filter((p) => p.status === 'EXPIRED' && p.completedAt)
      .map((p) => new Date(p.completedAt as string));
    return {
      labels: buckets.map((b) => b.label),
      datasets: [
        {
          // Started is enrollment volume, structurally always the
          // highest line since Completed/Expired are outcomes a subset
          // of it resolves to. Kept visually secondary via dashing +
          // thinner stroke (a self-explanatory convention, no caption
          // needed) rather than a faded color, since a washed-out blue
          // reads too close to Expired's gray at a glance.
          label: 'Started',
          data: this.countInBuckets(buckets, started),
          borderColor: COLOR_INFO,
          backgroundColor: 'transparent',
          borderDash: [5, 4],
          borderWidth: 1.5,
          tension: 0.3,
          pointRadius: 2,
          pointHoverRadius: 4,
          order: 3,
        },
        {
          label: 'Completed',
          data: this.countInBuckets(buckets, completed),
          borderColor: COLOR_SUCCESS,
          backgroundColor: 'transparent',
          borderWidth: 2.5,
          tension: 0.3,
          pointRadius: 3,
          pointHoverRadius: 5,
          order: 1,
        },
        {
          label: 'Expired',
          data: this.countInBuckets(buckets, expired),
          // A warm, distinctly non-blue gray — the previous #6b7c87 had
          // enough blue in it to blend with Started's blue dashed line.
          borderColor: '#78716c',
          backgroundColor: 'transparent',
          borderWidth: 2.5,
          tension: 0.3,
          pointRadius: 3,
          pointHoverRadius: 5,
          order: 2,
        },
      ],
    };
  });

  // Bucket-local (not cumulative) Completed / (Completed + Expired) per
  // period, using the exact same calculation as the lifetime KPI. A
  // bucket with zero resolved participations is `null` (gap in the
  // line), not 0% — 0% would misleadingly imply "resolved but none
  // completed" when really nothing resolved in that window at all.
  completionRateTrendData = computed<ChartData<'line'>>(() => {
    const buckets = this.completionRateBuckets();
    const all = this.participations();

    const rates: (number | null)[] = buckets.map((b) => {
      const completedInBucket = all.filter(
        (p) => p.status === 'COMPLETED' && p.completedAt && this.inBucket(new Date(p.completedAt as string), b)
      ).length;
      const expiredInBucket = all.filter(
        (p) => p.status === 'EXPIRED' && p.completedAt && this.inBucket(new Date(p.completedAt as string), b)
      ).length;
      const denom = completedInBucket + expiredInBucket;
      return denom === 0 ? null : Math.round((completedInBucket / denom) * 1000) / 10;
    });

    return {
      labels: buckets.map((b) => b.label),
      datasets: [
        {
          label: 'Challenge Completion Rate',
          data: rates,
          borderColor: COLOR_SUCCESS,
          backgroundColor: 'rgba(30, 122, 61, 0.10)',
          fill: true,
          tension: 0.3,
          pointRadius: 3,
          pointHoverRadius: 5,
          spanGaps: true,
        },
      ],
    };
  });

  // ── Empty-vs-loading-vs-zero state helpers ──
  // Distinguishes "this data source has never had any records" from
  // "there are records, just none in the selected window" — the latter
  // is a legitimate 0 and still renders as a flat chart at zero.

  usersNeverPopulated = computed(() => !this.usersLoading() && !this.usersError() && this.users().length === 0);
  challengesNeverPopulated = computed(
    () => !this.challengesLoading() && !this.challengesError() && this.challenges().length === 0
  );
  participationsNeverPopulated = computed(
    () => !this.participationsLoading() && !this.participationsError() && this.participations().length === 0
  );

  // ── Chart display options (shared minimal style) ──

  readonly lineChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: true, position: 'top', labels: { boxWidth: 10, usePointStyle: true, font: { size: 11 } } },
      tooltip: {
        enabled: true,
        callbacks: {
          label: (ctx) => `${ctx.dataset.label}: ${(ctx.parsed.y ?? 0).toLocaleString('en-US')}`,
        },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: { font: { size: 11 }, maxRotation: 0, autoSkip: true, maxTicksLimit: 9 },
      },
      y: {
        beginAtZero: true,
        grid: { color: '#edf0f1' },
        title: { display: true, text: 'Count', font: { size: 11 }, color: '#94a3b8' },
        ticks: { precision: 0, font: { size: 11 }, callback: (v) => Number(v).toLocaleString('en-US') },
      },
    },
  };

  // Registration Trend has only one series ("New Users"), so a legend
  // there just renders as a single clickable chip that toggles the only
  // line on/off — a filter control with nothing to actually filter.
  // Participation Activity (3 series) keeps the shared lineChartOptions
  // above, where the legend genuinely distinguishes Started/Completed/
  // Expired.
  readonly registrationChartOptions: ChartOptions<'line'> = {
    ...this.lineChartOptions,
    plugins: {
      ...this.lineChartOptions.plugins,
      legend: { display: false },
    },
  };

  readonly barChartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: true, position: 'top', labels: { boxWidth: 10, usePointStyle: true, font: { size: 11 } } },
      tooltip: {
        enabled: true,
        callbacks: {
          label: (ctx) => `${ctx.dataset.label}: ${(ctx.parsed.y ?? 0).toLocaleString('en-US')}`,
        },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: { font: { size: 11 }, maxRotation: 0, autoSkip: true, maxTicksLimit: 9 },
      },
      y: {
        beginAtZero: true,
        grid: { color: '#edf0f1' },
        title: { display: true, text: 'Challenges', font: { size: 11 }, color: '#94a3b8' },
        ticks: { precision: 0, font: { size: 11 }, callback: (v) => Number(v).toLocaleString('en-US') },
      },
    },
  };

  readonly completionRateChartPlugins: Plugin<'line'>[] = [percentPointLabelPlugin];

  readonly completionRateChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    layout: { padding: { top: 22 } },
    plugins: {
      legend: { display: false },
      tooltip: {
        enabled: true,
        callbacks: {
          label: (ctx) => `${ctx.dataset.label}: ${ctx.parsed.y === null ? 'No data' : ctx.parsed.y + '%'}`,
        },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: { font: { size: 11 }, maxRotation: 0, autoSkip: true, maxTicksLimit: 9 },
      },
      y: {
        beginAtZero: true,
        max: 100,
        grid: { color: '#edf0f1' },
        title: { display: true, text: 'Completion Rate (%)', font: { size: 11 }, color: '#94a3b8' },
        ticks: { font: { size: 11 }, callback: (v) => `${v}%` },
      },
    },
  };
}

export { Dashboard as AdminDashboardComponent };