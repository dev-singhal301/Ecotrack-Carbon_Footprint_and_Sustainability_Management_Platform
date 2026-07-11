package com.ecotrack.ecotrack_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public class GoalRequest {

    @NotBlank
    private String goalName;

    @NotBlank
    private String goalType;

    @NotNull
    @Positive
    private BigDecimal targetValue;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @PositiveOrZero
    private Integer rewardPoints; 

    public GoalRequest() {}

    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(Integer rewardPoints) { this.rewardPoints = rewardPoints; }
}