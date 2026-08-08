package com.ecotrack.ecotrack_backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Admin-authored challenge definition. Maps to the 'challenges' table.
 *
 * Deliberately has no stored lifecycle status field — UPCOMING/ACTIVE/ENDED
 * is derived at read time from `cancelled` + today's date vs. start/end
 * date (see schema.sql's comment on the challenges table for why: a stored
 * status column would be a second source of truth that can drift out of
 * sync with the dates). ChallengeResponse computes the derived status for
 * the frontend.
 */
@Entity
@Table(name = "challenges")
public class Challenge {

    // Business rule shared by UserChallengeService (enforces it on join)
    // and ChallengeResponse (surfaces it to the frontend as
    // joinWindowClosed) — kept in one place so the two can't drift apart.
    // A challenge can be joined from its start_date through
    // start_date + JOIN_WINDOW_DAYS, inclusive.
    public static final int JOIN_WINDOW_DAYS = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "challenge_id")
    private Long challengeId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_type", nullable = false, length = 50)
    private ChallengeType challengeType;

    // Activity challenges only — null for CARBON_REDUCTION
    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_type", length = 50)
    private TrackingType trackingType;

    @Column(name = "target_value", nullable = false)
    private BigDecimal targetValue;

    @Column(length = 50)
    private String unit;

    @Column(name = "reward_xp")
    private Integer rewardXp = 100;

    @Column(name = "badge_image_url", length = 255)
    private String badgeImageUrl;

    @Column(name = "card_image_url", length = 255)
    private String cardImageUrl;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // The admin who created this challenge — set server-side from the
    // authenticated principal, never trusted from the request body.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private boolean cancelled = false;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.rewardXp == null) this.rewardXp = 100;
        if (this.cancelled == false) this.cancelled = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Challenge() {}

    // Getters and Setters
    public Long getChallengeId() { return challengeId; }
    public void setChallengeId(Long challengeId) { this.challengeId = challengeId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public ChallengeType getChallengeType() { return challengeType; }
    public void setChallengeType(ChallengeType challengeType) { this.challengeType = challengeType; }

    public TrackingType getTrackingType() { return trackingType; }
    public void setTrackingType(TrackingType trackingType) { this.trackingType = trackingType; }

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

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}