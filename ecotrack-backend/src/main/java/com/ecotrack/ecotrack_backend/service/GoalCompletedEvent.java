package com.ecotrack.ecotrack_backend.service;

import org.springframework.context.ApplicationEvent;

/**
 * Fired the moment a Goal transitions to COMPLETED — whether the user hit
 * "Complete" explicitly, or GoalService auto-completed it on read (still
 * within budget after the end date) or via the daily maintenance sweep.
 * XP/points are already awarded by the time this fires. Mirrors
 * ChallengeCompletedEvent; consumed by the Notification module.
 */
public class GoalCompletedEvent extends ApplicationEvent {

    private final Long userId;
    private final Long goalId;
    private final int xpAwarded;

    public GoalCompletedEvent(Object source, Long userId, Long goalId, int xpAwarded) {
        super(source);
        this.userId = userId;
        this.goalId = goalId;
        this.xpAwarded = xpAwarded;
    }

    public Long getUserId() { return userId; }
    public Long getGoalId() { return goalId; }
    public int getXpAwarded() { return xpAwarded; }
}