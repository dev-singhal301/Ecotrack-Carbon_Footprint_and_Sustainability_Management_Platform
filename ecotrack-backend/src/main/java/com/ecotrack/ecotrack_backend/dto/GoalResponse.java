package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class GoalResponse {

    private Long goalId;
    private String goalName;
    private String goalType;
    private BigDecimal targetValue;
    private Integer rewardPoints;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal baselineValue;

    public GoalResponse() {
    }

    public GoalResponse(Long goalId, String goalName, String goalType, BigDecimal targetValue,
            BigDecimal baselineValue, Integer rewardPoints, String status,
            LocalDate startDate, LocalDate endDate) {
        this.goalId = goalId;
        this.goalName = goalName;
        this.goalType = goalType;
        this.targetValue = targetValue;
        this.baselineValue = baselineValue;
        this.rewardPoints = rewardPoints;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getGoalId() {
        return goalId;
    }

    public void setGoalId(Long goalId) {
        this.goalId = goalId;
    }

    public String getGoalName() {
        return goalName;
    }

    public void setGoalName(String goalName) {
        this.goalName = goalName;
    }

    public String getGoalType() {
        return goalType;
    }

    public void setGoalType(String goalType) {
        this.goalType = goalType;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
    }

    public Integer getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(Integer rewardPoints) {
        this.rewardPoints = rewardPoints;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getBaselineValue() {
        return baselineValue;
    }

    public void setBaselineValue(BigDecimal baselineValue) {
        this.baselineValue = baselineValue;
    }
}