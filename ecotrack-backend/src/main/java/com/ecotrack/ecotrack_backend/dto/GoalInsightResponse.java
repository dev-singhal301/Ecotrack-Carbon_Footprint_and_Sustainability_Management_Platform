package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;

public record GoalInsightResponse(
        Long goalId,
        String goalName,
        String goalType,
        BigDecimal currentValue,
        BigDecimal targetValue,
        BigDecimal completionPercentage,
        boolean completed,
        String endDate,
        GoalInsightDetail insight
) {}