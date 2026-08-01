package com.ecotrack.ecotrack_backend.dto;

import com.ecotrack.ecotrack_backend.entity.Challenge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ChallengeResponse {

    private Long challengeId;
    private String title;
    private String description;
    private String category;
    private String challengeType;
    private String trackingType;
    private BigDecimal targetValue;
    private String unit;
    private Integer rewardXp;
    private String badgeImageUrl;
    private String cardImageUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long createdBy;
    private boolean cancelled;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Derived lifecycle state — UPCOMING / ACTIVE / ENDED / CANCELLED.
    // Computed here, once, server-side, rather than in the frontend or
    // stored on the entity — see Challenge.java's doc comment for why
    // there's no stored status column.
    private String status;

    public ChallengeResponse() {}

    public static ChallengeResponse fromEntity(Challenge challenge) {
        ChallengeResponse response = new ChallengeResponse();
        response.challengeId = challenge.getChallengeId();
        response.title = challenge.getTitle();
        response.description = challenge.getDescription();
        response.category = challenge.getCategory();
        response.challengeType = challenge.getChallengeType() != null ? challenge.getChallengeType().name() : null;
        response.trackingType = challenge.getTrackingType() != null ? challenge.getTrackingType().name() : null;
        response.targetValue = challenge.getTargetValue();
        response.unit = challenge.getUnit();
        response.rewardXp = challenge.getRewardXp();
        response.badgeImageUrl = challenge.getBadgeImageUrl();
        response.cardImageUrl = challenge.getCardImageUrl();
        response.startDate = challenge.getStartDate();
        response.endDate = challenge.getEndDate();
        response.createdBy = challenge.getCreatedBy() != null ? challenge.getCreatedBy().getId() : null;
        response.cancelled = challenge.isCancelled();
        response.cancelledAt = challenge.getCancelledAt();
        response.createdAt = challenge.getCreatedAt();
        response.updatedAt = challenge.getUpdatedAt();
        response.status = deriveStatus(challenge);
        return response;
    }

    private static String deriveStatus(Challenge challenge) {
        if (challenge.isCancelled()) {
            return "CANCELLED";
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(challenge.getStartDate())) {
            return "UPCOMING";
        }
        if (today.isAfter(challenge.getEndDate())) {
            return "ENDED";
        }
        return "ACTIVE";
    }

    public Long getChallengeId() { return challengeId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getChallengeType() { return challengeType; }
    public String getTrackingType() { return trackingType; }
    public BigDecimal getTargetValue() { return targetValue; }
    public String getUnit() { return unit; }
    public Integer getRewardXp() { return rewardXp; }
    public String getBadgeImageUrl() { return badgeImageUrl; }
    public String getCardImageUrl() { return cardImageUrl; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Long getCreatedBy() { return createdBy; }
    public boolean isCancelled() { return cancelled; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getStatus() { return status; }
}