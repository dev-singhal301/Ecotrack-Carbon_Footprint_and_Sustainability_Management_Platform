package com.ecotrack.ecotrack_backend.dto;

import com.ecotrack.ecotrack_backend.entity.UserChallenge;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserChallengeResponse {

    private Long userChallengeId;
    private ChallengeResponse challenge;
    private String userStatus; // NOT_JOINED, JOINED, IN_PROGRESS, COMPLETED, EXPIRED, CANCELLED
    private BigDecimal currentValue;
    private BigDecimal completionPercentage;
    private BigDecimal baselineValue;
    private BigDecimal effectiveTarget;
    private LocalDateTime joinedAt;
    private LocalDateTime completedAt;

    public UserChallengeResponse() {}

    public static UserChallengeResponse fromEntity(ChallengeResponse challengeResponse, UserChallenge userChallenge) {
        UserChallengeResponse response = new UserChallengeResponse();
        response.challenge = challengeResponse;

        if (userChallenge != null) {
            response.userChallengeId = userChallenge.getId();
            response.userStatus = userChallenge.getStatus().name();
            response.currentValue = userChallenge.getCurrentValue();
            response.completionPercentage = userChallenge.getCompletionPercentage();
            response.baselineValue = userChallenge.getBaselineValue();
            response.effectiveTarget = userChallenge.getEffectiveTarget();
            response.joinedAt = userChallenge.getJoinedAt();
            response.completedAt = userChallenge.getCompletedAt();
        } else {
            response.userStatus = "NOT_JOINED";
            response.currentValue = BigDecimal.ZERO;
            response.completionPercentage = BigDecimal.ZERO;
        }

        return response;
    }

    // Getters and Setters
    public Long getUserChallengeId() { return userChallengeId; }
    public ChallengeResponse getChallenge() { return challenge; }
    public String getUserStatus() { return userStatus; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public BigDecimal getCompletionPercentage() { return completionPercentage; }
    public BigDecimal getBaselineValue() { return baselineValue; }
    public BigDecimal getEffectiveTarget() { return effectiveTarget; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}