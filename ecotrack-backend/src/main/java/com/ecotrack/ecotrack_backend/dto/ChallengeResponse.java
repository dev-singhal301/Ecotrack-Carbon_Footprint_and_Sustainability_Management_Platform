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

    // True once the join window (start_date .. start_date + JOIN_WINDOW_DAYS)
    // has passed. Lets the frontend show "join window closed" on a
    // still-ACTIVE, not-yet-joined challenge without duplicating the rule
    // UserChallengeService.joinChallenge() already enforces server-side.
    private boolean joinWindowClosed;

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
        response.joinWindowClosed = deriveJoinWindowClosed(challenge);
        return response;
    }

    private static String deriveStatus(Challenge challenge) {
       if (challenge.isCancelled()) {
           return "CANCELLED";
       }

       LocalDate now = LocalDate.now();
       LocalDate startDate = challenge.getStartDate();
       LocalDate endDate = challenge.getEndDate();

       if (startDate == null || endDate == null) {
           return "UPCOMING"; // Default fallback if dates are unassigned
       }

       if (now.isBefore(startDate)) {
           return "UPCOMING";
       } else if (now.isAfter(endDate)) {
           return "ENDED";
       } else {
           return "ACTIVE";
       }
    }

    private static boolean deriveJoinWindowClosed(Challenge challenge) {
        LocalDate startDate = challenge.getStartDate();
        if (startDate == null) {
            return false;
        }
        LocalDate joinDeadline = startDate.plusDays(Challenge.JOIN_WINDOW_DAYS);
        return LocalDate.now().isAfter(joinDeadline);
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
    public boolean isJoinWindowClosed() { return joinWindowClosed; }
}