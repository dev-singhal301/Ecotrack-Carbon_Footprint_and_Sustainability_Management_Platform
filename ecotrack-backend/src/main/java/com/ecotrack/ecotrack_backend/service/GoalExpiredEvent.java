package com.ecotrack.ecotrack_backend.service;

import org.springframework.context.ApplicationEvent;

/**
 * Fired when a Goal's end date passes (or its target is breached) without
 * it being completed. No XP implication, informational only. Mirrors
 * ChallengeExpiredEvent; consumed by the Notification module.
 */
public class GoalExpiredEvent extends ApplicationEvent {

    private final Long userId;
    private final Long goalId;

    public GoalExpiredEvent(Object source, Long userId, Long goalId) {
        super(source);
        this.userId = userId;
        this.goalId = goalId;
    }

    public Long getUserId() { return userId; }
    public Long getGoalId() { return goalId; }
}