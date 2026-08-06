package com.ecotrack.ecotrack_backend.service;

import org.springframework.context.ApplicationEvent;

/**
 * Fired the moment a UserChallenge transitions to COMPLETED — either an
 * ACTIVITY_BASED challenge whose logged total just reached its target, or a
 * CARBON_REDUCTION challenge whose period just ended with the user under
 * their frozen effective target.
 *
 * XP is already awarded by the time this fires (UserChallengeService calls
 * LeaderboardService directly, synchronously, so it can't silently fail).
 * This event exists purely as the integration point for Person C's
 * Gamification module (BadgeService threshold checks) and Notification
 * module — see Challenge Module Design doc §8.
 */
public class ChallengeCompletedEvent extends ApplicationEvent {

    private final Long userId;
    private final Long challengeId;
    private final Long userChallengeId;
    private final int xpAwarded;

    public ChallengeCompletedEvent(Object source, Long userId, Long challengeId, Long userChallengeId, int xpAwarded) {
        super(source);
        this.userId = userId;
        this.challengeId = challengeId;
        this.userChallengeId = userChallengeId;
        this.xpAwarded = xpAwarded;
    }

    public Long getUserId() { return userId; }
    public Long getChallengeId() { return challengeId; }
    public Long getUserChallengeId() { return userChallengeId; }
    public int getXpAwarded() { return xpAwarded; }
}
