package com.ecotrack.ecotrack_backend.dto;

import java.util.List;

public class DashboardSummaryResponse {
    private FootprintOverviewResponse footprintOverview;
    private FootprintTrendResponse trend;
    private GoalSnapshotResponse goalSnapshot;
    private List<GoalHighlightResponse> nearingCompletionOrDeadline;
    private List<RecentActivityResponse> recentActivity;

    public DashboardSummaryResponse() {}
    public DashboardSummaryResponse(FootprintOverviewResponse footprintOverview, FootprintTrendResponse trend,
                                     GoalSnapshotResponse goalSnapshot,
                                     List<GoalHighlightResponse> nearingCompletionOrDeadline,
                                     List<RecentActivityResponse> recentActivity) {
        this.footprintOverview = footprintOverview;
        this.trend = trend;
        this.goalSnapshot = goalSnapshot;
        this.nearingCompletionOrDeadline = nearingCompletionOrDeadline;
        this.recentActivity = recentActivity;
    }
    public FootprintOverviewResponse getFootprintOverview() { return footprintOverview; }
    public void setFootprintOverview(FootprintOverviewResponse footprintOverview) { this.footprintOverview = footprintOverview; }
    public FootprintTrendResponse getTrend() { return trend; }
    public void setTrend(FootprintTrendResponse trend) { this.trend = trend; }
    public GoalSnapshotResponse getGoalSnapshot() { return goalSnapshot; }
    public void setGoalSnapshot(GoalSnapshotResponse goalSnapshot) { this.goalSnapshot = goalSnapshot; }
    public List<GoalHighlightResponse> getNearingCompletionOrDeadline() { return nearingCompletionOrDeadline; }
    public void setNearingCompletionOrDeadline(List<GoalHighlightResponse> nearingCompletionOrDeadline) { this.nearingCompletionOrDeadline = nearingCompletionOrDeadline; }
    public List<RecentActivityResponse> getRecentActivity() { return recentActivity; }
    public void setRecentActivity(List<RecentActivityResponse> recentActivity) { this.recentActivity = recentActivity; }
}