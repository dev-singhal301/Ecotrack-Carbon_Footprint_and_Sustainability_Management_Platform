package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class GoalProgressResponse {

    private Long goalId;
    private BigDecimal currentValue;
    private BigDecimal targetValue;
    private BigDecimal completionPercentage;
    private boolean completed;
    private LocalDate completedDate;
    private LocalDateTime lastUpdated;
    private BigDecimal baselineValue;

    public GoalProgressResponse() {
    }

    // Original constructor (retained for backward compatibility)
    public GoalProgressResponse(Long goalId, BigDecimal currentValue, BigDecimal targetValue,
                                BigDecimal baselineValue, BigDecimal completionPercentage,
                                boolean completed, LocalDateTime lastUpdated) {
        this.goalId = goalId;
        this.currentValue = currentValue;
        this.targetValue = targetValue;
        this.baselineValue = baselineValue;
        this.completionPercentage = completionPercentage;
        this.completed = completed;
        this.lastUpdated = lastUpdated;
    }

    // Extended constructor with completedDate
    public GoalProgressResponse(Long goalId, BigDecimal currentValue, BigDecimal targetValue,
                                BigDecimal baselineValue, BigDecimal completionPercentage,
                                boolean completed, LocalDate completedDate, LocalDateTime lastUpdated) {
        this.goalId = goalId;
        this.currentValue = currentValue;
        this.targetValue = targetValue;
        this.baselineValue = baselineValue;
        this.completionPercentage = completionPercentage;
        this.completed = completed;
        this.completedDate = completedDate;
        this.lastUpdated = lastUpdated;
    }

    public Long getGoalId() {
        return goalId;
    }

    public void setGoalId(Long goalId) {
        this.goalId = goalId;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
    }

    public BigDecimal getCompletionPercentage() {
        return completionPercentage;
    }

    public void setCompletionPercentage(BigDecimal completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public LocalDate getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(LocalDate completedDate) {
        this.completedDate = completedDate;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public BigDecimal getBaselineValue() {
        return baselineValue;
    }

    public void setBaselineValue(BigDecimal baselineValue) {
        this.baselineValue = baselineValue;
    }
}