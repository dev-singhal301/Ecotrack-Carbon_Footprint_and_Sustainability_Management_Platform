package com.ecotrack.ecotrack_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ChallengeRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Challenge type is required")
    private String challengeType; // "CARBON_REDUCTION" or "ACTIVITY_BASED"

    // Required only for ACTIVITY_BASED — cross-field checks happen in
    // ChallengeService, not here, since validity depends on challengeType.
    private String trackingType;

    @NotNull(message = "Target value is required")
    @Positive(message = "Target value must be greater than zero")
    private BigDecimal targetValue;

    private String unit;

    @PositiveOrZero(message = "Reward XP cannot be negative")
    private Integer rewardXp;

    private String badgeImageUrl;

    private String cardImageUrl;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    public ChallengeRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getChallengeType() { return challengeType; }
    public void setChallengeType(String challengeType) { this.challengeType = challengeType; }

    public String getTrackingType() { return trackingType; }
    public void setTrackingType(String trackingType) { this.trackingType = trackingType; }

    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getRewardXp() { return rewardXp; }
    public void setRewardXp(Integer rewardXp) { this.rewardXp = rewardXp; }

    public String getBadgeImageUrl() { return badgeImageUrl; }
    public void setBadgeImageUrl(String badgeImageUrl) { this.badgeImageUrl = badgeImageUrl; }

    public String getCardImageUrl() { return cardImageUrl; }
    public void setCardImageUrl(String cardImageUrl) { this.cardImageUrl = cardImageUrl; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}