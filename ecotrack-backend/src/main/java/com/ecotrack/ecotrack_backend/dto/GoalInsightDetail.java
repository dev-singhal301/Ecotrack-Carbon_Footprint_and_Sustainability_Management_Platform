package com.ecotrack.ecotrack_backend.dto;

public record GoalInsightDetail(
        String status,   // ON_TRACK / AT_RISK / OVER_BUDGET / FAILED / COMPLETED
        String message,  // narrative paragraph, AI or rule-based
        String action,   // one concrete next step
        String source    // "AI" or "STANDARD" — never the literal word "gemini"
) {}