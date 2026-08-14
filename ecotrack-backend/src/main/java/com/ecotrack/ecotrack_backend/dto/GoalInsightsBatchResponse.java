package com.ecotrack.ecotrack_backend.dto;

import java.util.List;

public record GoalInsightsBatchResponse(
        List<GoalInsightResponse> insights
) {}