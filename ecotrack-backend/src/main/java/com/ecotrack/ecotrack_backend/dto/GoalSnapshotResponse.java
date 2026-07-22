package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;

public class GoalSnapshotResponse {

    private Long goalId;
    private String goalName;
    private String goalType;
    private BigDecimal targetValue;
    private BigDecimal currentValue;
    private BigDecimal completionPercentage;
    private Integer rewardPoints;

    public GoalSnapshotResponse() {}

    public GoalSnapshotResponse(Long goalId, String goalName, String goalType, BigDecimal targetValue,
                                BigDecimal currentValue, BigDecimal completionPercentage, Integer rewardPoints) {
        this.goalId = goalId;
        this.goalName = goalName;
        this.goalType = goalType;
        this.targetValue = targetValue;
        this.currentValue = currentValue;
        this.completionPercentage = completionPercentage;
        this.rewardPoints = rewardPoints;
    }

    // Getters and Setters
    public Long getGoalId() { return goalId; }
    public void setGoalId(Long goalId) { this.goalId = goalId; }

    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }

    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }

    public BigDecimal getCompletionPercentage() { return completionPercentage; }
    public void setCompletionPercentage(BigDecimal completionPercentage) { this.completionPercentage = completionPercentage; }

    public Integer getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(Integer rewardPoints) { this.rewardPoints = rewardPoints; }
}