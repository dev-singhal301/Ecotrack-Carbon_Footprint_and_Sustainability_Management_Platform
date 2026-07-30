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