// Matches GET /api/admin/dashboard-summary exactly, per
// AdminDashboardSummaryResponse.java. Intentionally user-stats-only for
// now — see that class's doc comment for why challenge stats aren't here.
export interface AdminDashboardSummary {
  totalUsers: number;
  activeUsers: number;
  disabledUsers: number;
  totalAdmins: number;
  newUsersThisWeek: number;
}

// Matches GET /api/admin/participations exactly, per
// AdminParticipationResponse.java. Note: completedAt also holds the
// resolution timestamp for EXPIRED participations, not just COMPLETED —
// see UserChallenge.completedAt's doc comment on the backend entity.
export interface AdminParticipation {
  userChallengeId: number;
  challengeId: number;
  status: 'JOINED' | 'IN_PROGRESS' | 'COMPLETED' | 'EXPIRED' | 'CANCELLED';
  joinedAt: string;
  completedAt: string | null;
}

// Date-range filter driving the dashboard's historical/trend sections only.
// Current-state KPIs and snapshots are never affected by this.
export type DashboardDateRange = '7d' | '30d' | '90d' | 'year' | 'custom';

// One bucketed point in a time-series trend — a day or a week depending
// on how wide the selected range is (see dashboard.ts bucketing logic).
export interface TrendBucket {
  label: string;
  bucketStart: Date;
  bucketEnd: Date;
}