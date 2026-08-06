package com.ecotrack.ecotrack_backend.service;

import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Fired once per admin cancellation, carrying every affected participant's
 * user id, so Person C's Notification module can inform them their
 * challenge was cancelled — explicitly NOT that they "failed"
 * (Challenge Module Design doc §8, Workflow doc §13).
 */
public class ChallengeCancelledEvent extends ApplicationEvent {

    private final Long challengeId;
    private final List<Long> affectedUserIds;

    public ChallengeCancelledEvent(Object source, Long challengeId, List<Long> affectedUserIds) {
        super(source);
        this.challengeId = challengeId;
        this.affectedUserIds = affectedUserIds;
    }

    public Long getChallengeId() { return challengeId; }
    public List<Long> getAffectedUserIds() { return affectedUserIds; }
}
