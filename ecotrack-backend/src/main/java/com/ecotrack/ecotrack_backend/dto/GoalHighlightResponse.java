package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;

public class GoalHighlightResponse {
    private Long goalId;
    private String goalName;
    private String goalType;
    private BigDecimal completionPercentage;
    private long daysRemaining; // negative if the goal is overdue but hasn't been marked EXPIRED yet

    public GoalHighlightResponse() {}
    public GoalHighlightResponse(Long goalId, String goalName, String goalType,
                                  BigDecimal completionPercentage, long daysRemaining) {
        this.goalId = goalId;
        this.goalName = goalName;
        this.goalType = goalType;
        this.completionPercentage = completionPercentage;
        this.daysRemaining = daysRemaining;
    }
    public Long getGoalId() { return goalId; }
    public void setGoalId(Long goalId) { this.goalId = goalId; }
    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }
    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }
    public BigDecimal getCompletionPercentage() { return completionPercentage; }
    public void setCompletionPercentage(BigDecimal completionPercentage) { this.completionPercentage = completionPercentage; }
    public long getDaysRemaining() { return daysRemaining; }
    public void setDaysRemaining(long daysRemaining) { this.daysRemaining = daysRemaining; }
}