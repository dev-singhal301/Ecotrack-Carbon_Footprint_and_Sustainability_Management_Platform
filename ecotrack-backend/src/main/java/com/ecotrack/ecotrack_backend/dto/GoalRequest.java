package com.ecotrack.ecotrack_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class GoalRequest {

    @NotBlank(message = "Goal name is required")
    private String goalName;

    @NotBlank(message = "Goal type is required")
    private String goalType;

    @NotNull(message = "Target value is required")
    @Positive(message = "Target value must be greater than zero")
    private BigDecimal targetValue;

    @NotNull(message = "Duration in days is required")
    @Positive(message = "Duration must be at least 1 day")
    private Integer durationDays;

    @PositiveOrZero(message = "Reward points cannot be negative")
    private Integer rewardPoints;

    @PositiveOrZero(message = "XP reward cannot be negative")
    private Integer xpReward;

    public GoalRequest() {}

    public GoalRequest(String goalName, String goalType, BigDecimal targetValue, Integer durationDays, Integer rewardPoints, Integer xpReward) {
        this.goalName = goalName;
        this.goalType = goalType;
        this.targetValue = targetValue;
        this.durationDays = durationDays;
        this.rewardPoints = rewardPoints;
        this.xpReward = xpReward;
    }

    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }

    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }

    public Integer getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(Integer rewardPoints) { this.rewardPoints = rewardPoints; }

    public Integer getXpReward() { return xpReward; }
    public void setXpReward(Integer xpReward) { this.xpReward = xpReward; }
}