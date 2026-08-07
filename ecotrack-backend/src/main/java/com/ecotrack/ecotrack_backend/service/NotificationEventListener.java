package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.entity.Challenge;
import com.ecotrack.ecotrack_backend.entity.ChallengeType;
import com.ecotrack.ecotrack_backend.entity.Goal;
import com.ecotrack.ecotrack_backend.repository.ChallengeRepository;
import com.ecotrack.ecotrack_backend.repository.GoalRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Single integration point where every goal/challenge lifecycle event
 * becomes a row in the notifications table. Listens with AFTER_COMMIT so a
 * notification is only ever written once the triggering change (XP award,
 * status flip, etc.) has actually committed — if that transaction rolls
 * back, no stray notification is left behind.
 */
@Component
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final ChallengeRepository challengeRepository;
    private final GoalRepository goalRepository;

    public NotificationEventListener(NotificationService notificationService,
                                      ChallengeRepository challengeRepository,
                                      GoalRepository goalRepository) {
        this.notificationService = notificationService;
        this.challengeRepository = challengeRepository;
        this.goalRepository = goalRepository;
    }

    // ── Challenges ──

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChallengeCompleted(ChallengeCompletedEvent event) {
        String title = challengeTitle(event.getChallengeId());
        notificationService.createNotification(
                event.getUserId(),
                "Challenge completed!",
                "You completed \"" + title + "\" and earned " + event.getXpAwarded() + " XP.",
                "CHALLENGE_COMPLETED");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChallengeExpired(ChallengeExpiredEvent event) {
        Challenge challenge = challengeRepository.findById(event.getChallengeId()).orElse(null);
        String title = challenge != null ? challenge.getTitle() : "a challenge";

        // CARBON_REDUCTION challenges are budget-style, same as goals — they
        // expire because the user went over their limit. ACTIVITY_BASED
        // (and legacy untyped) challenges expire because a target wasn't
        // reached in time — different reason, different message.
        String message = (challenge != null && challenge.getChallengeType() == ChallengeType.CARBON_REDUCTION)
                ? "\"" + title + "\" ended because you exceeded your limit. Better luck next time!"
                : "\"" + title + "\" ended, and you didn't meet the target. Better luck next time!";

        notificationService.createNotification(
                event.getUserId(),
                "Challenge expired",
                message,
                "CHALLENGE_EXPIRED");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChallengeCancelled(ChallengeCancelledEvent event) {
        String title = challengeTitle(event.getChallengeId());
        for (Long userId : event.getAffectedUserIds()) {
            notificationService.createNotification(
                    userId,
                    "Challenge cancelled",
                    "\"" + title + "\" was cancelled by an admin. This won't count against you.",
                    "CHALLENGE_CANCELLED");
        }
    }

    // ── Goals ──

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGoalCompleted(GoalCompletedEvent event) {
        String name = goalName(event.getGoalId());
        notificationService.createNotification(
                event.getUserId(),
                "Goal completed!",
                "You completed \"" + name + "\" and earned " + event.getXpAwarded() + " XP.",
                "GOAL_COMPLETED");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGoalExpired(GoalExpiredEvent event) {
        String name = goalName(event.getGoalId());
        notificationService.createNotification(
                event.getUserId(),
                "Goal expired",
                "\"" + name + "\" ended because you exceeded your limit. You can set a new goal anytime.",
                "GOAL_EXPIRED");
    }

    // ── Lookup helpers (best-effort — a missing title shouldn't block the notification) ──

    private String challengeTitle(Long challengeId) {
        return challengeRepository.findById(challengeId)
                .map(Challenge::getTitle)
                .orElse("a challenge");
    }

    private String goalName(Long goalId) {
        return goalRepository.findById(goalId)
                .map(Goal::getGoalName)
                .orElse("a goal");
    }
}