package com.ecotrack.ecotrack_backend.dto;

/**
 * Response for GET /api/admin/dashboard-summary.
 *
 * Deliberately limited to what's backed by real data right now: the
 * `users` table. Challenge/participation stats (total challenges, active
 * challenges, join counts, etc.) are NOT included here — the challenges
 * and user_challenges tables exist in schema.sql, but there's no
 * Challenge/UserChallenge JPA entity or repository yet, so there's
 * nothing real to query. Those fields get added to this response once
 * the Challenge entity/service work lands, not before.
 */
public class AdminDashboardSummaryResponse {

    private long totalUsers;
    private long activeUsers;
    private long disabledUsers;
    private long totalAdmins;
    private long newUsersThisWeek;

    public AdminDashboardSummaryResponse() {}

    public AdminDashboardSummaryResponse(long totalUsers, long activeUsers, long disabledUsers,
                                          long totalAdmins, long newUsersThisWeek) {
        this.totalUsers = totalUsers;
        this.activeUsers = activeUsers;
        this.disabledUsers = disabledUsers;
        this.totalAdmins = totalAdmins;
        this.newUsersThisWeek = newUsersThisWeek;
    }

    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }

    public long getActiveUsers() { return activeUsers; }
    public void setActiveUsers(long activeUsers) { this.activeUsers = activeUsers; }

    public long getDisabledUsers() { return disabledUsers; }
    public void setDisabledUsers(long disabledUsers) { this.disabledUsers = disabledUsers; }

    public long getTotalAdmins() { return totalAdmins; }
    public void setTotalAdmins(long totalAdmins) { this.totalAdmins = totalAdmins; }

    public long getNewUsersThisWeek() { return newUsersThisWeek; }
    public void setNewUsersThisWeek(long newUsersThisWeek) { this.newUsersThisWeek = newUsersThisWeek; }
}