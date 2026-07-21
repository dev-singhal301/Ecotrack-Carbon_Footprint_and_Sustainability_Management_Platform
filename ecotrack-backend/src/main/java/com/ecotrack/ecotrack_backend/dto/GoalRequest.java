package com.ecotrack.ecotrack_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class GoalRequest {

    @NotBlank
    private String goalName;

    @NotBlank
    private String goalType;

    @NotNull
    @Positive
    private BigDecimal targetValue;

    // ── Swapped fields ──
    @NotNull
    @Positive
    private Integer durationDays; // Replacing explicit start/end dates

    @PositiveOrZero
    private Integer rewardPoints; 

    public GoalRequest() {}

    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }

    // ── Getter & Setter for durationDays ──
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }

    public Integer getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(Integer rewardPoints) { this.rewardPoints = rewardPoints;}
}