package com.ecotrack.ecotrack_backend.dto;

import java.time.LocalDateTime;

public class XpActivityResponse {
    private Long id;
    private String sourceType;  // e.g., "GOAL", "CHALLENGE", "DAILY_LOG", "STREAK"
    private String description; // e.g., "Cut Electricity Goal Completed"
    private Integer xpEarned;   // e.g., 50
    private LocalDateTime createdAt;

    public XpActivityResponse() {}

    public XpActivityResponse(Long id, String sourceType, String description, Integer xpEarned, LocalDateTime createdAt) {
        this.id = id;
        this.sourceType = sourceType;
        this.description = description;
        this.xpEarned = xpEarned;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getXpEarned() { return xpEarned; }
    public void setXpEarned(Integer xpEarned) { this.xpEarned = xpEarned; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}