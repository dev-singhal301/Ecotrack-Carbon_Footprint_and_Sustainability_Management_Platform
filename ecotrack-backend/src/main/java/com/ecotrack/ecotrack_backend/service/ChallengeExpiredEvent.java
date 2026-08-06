package com.ecotrack.ecotrack_backend.service;

import org.springframework.context.ApplicationEvent;

/**
 * Fired when a UserChallenge's challenge end date passes without the user
 * reaching the target — no XP implication, informational only. Consumed by
 * Person C's Notification module (Challenge Module Design doc §8).
 */
public class ChallengeExpiredEvent extends ApplicationEvent {

    private final Long userId;
    private final Long challengeId;
    private final Long userChallengeId;

    public ChallengeExpiredEvent(Object source, Long userId, Long challengeId, Long userChallengeId) {
        super(source);
        this.userId = userId;
        this.challengeId = challengeId;
        this.userChallengeId = userChallengeId;
    }

    public Long getUserId() { return userId; }
    public Long getChallengeId() { return challengeId; }
    public Long getUserChallengeId() { return userChallengeId; }
}
