package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GoalProgressResponse {

    private Long goalId;
    private BigDecimal currentValue;
    private BigDecimal targetValue;
    private BigDecimal completionPercentage;
    private boolean completed;
    private LocalDateTime lastUpdated;

    public GoalProgressResponse() {}

    public GoalProgressResponse(Long goalId, BigDecimal currentValue, BigDecimal targetValue,
                                 BigDecimal completionPercentage, boolean completed, LocalDateTime lastUpdated) {
        this.goalId = goalId;
        this.currentValue = currentValue;
        this.targetValue = targetValue;
        this.completionPercentage = completionPercentage;
        this.completed = completed;
        this.lastUpdated = lastUpdated;
    }

    public Long getGoalId() { return goalId; }
    public void setGoalId(Long goalId) { this.goalId = goalId; }

    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }

    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }

    public BigDecimal getCompletionPercentage() { return completionPercentage; }
    public void setCompletionPercentage(BigDecimal completionPercentage) { this.completionPercentage = completionPercentage; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}