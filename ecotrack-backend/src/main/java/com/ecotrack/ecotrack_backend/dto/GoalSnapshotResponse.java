package com.ecotrack.ecotrack_backend.dto;

public class GoalSnapshotResponse {
    private long activeCount;
    private long completedCount;

    public GoalSnapshotResponse() {}
    public GoalSnapshotResponse(long activeCount, long completedCount) {
        this.activeCount = activeCount;
        this.completedCount = completedCount;
    }
    public long getActiveCount() { return activeCount; }
    public void setActiveCount(long activeCount) { this.activeCount = activeCount; }
    public long getCompletedCount() { return completedCount; }
    public void setCompletedCount(long completedCount) { this.completedCount = completedCount; }
}