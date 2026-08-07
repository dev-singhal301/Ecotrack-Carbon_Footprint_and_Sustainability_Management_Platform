package com.ecotrack.ecotrack_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Force-settles any goal/challenge whose window has already closed, even if
 * its owner hasn't opened the app since. Without this sweep, expiration is
 * only ever detected lazily (checkAndHandleExpiration / refreshAndFinalize,
 * triggered when a user opens the Goals/Challenges screen) — meaning a user
 * who doesn't log back in would never be flagged as expired/completed, and
 * would never get the corresponding notification either, since
 * GoalExpiredEvent/ChallengeExpiredEvent only fire when the status flip
 * actually happens.
 *
 * Runs daily at 00:10, comfortably after midnight so any goal/challenge
 * whose endDate was "yesterday" has definitely passed.
 */
@Component
public class DailyMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyMaintenanceScheduler.class);

    private final GoalService goalService;
    private final UserChallengeService userChallengeService;

    public DailyMaintenanceScheduler(GoalService goalService, UserChallengeService userChallengeService) {
        this.goalService = goalService;
        this.userChallengeService = userChallengeService;
    }

    @Scheduled(cron = "0 10 0 * * *")
    public void runDailySweep() {
        try {
            int goalsPastEndDate = goalService.finalizeAllActiveGoals();
            log.info("Daily maintenance: swept ACTIVE goals, {} were past their end date", goalsPastEndDate);
        } catch (Exception ex) {
            // One module's failure shouldn't stop the other's sweep from running.
            log.error("Daily maintenance: goal sweep failed", ex);
        }

        try {
            int challengesSwept = userChallengeService.finalizeAllInProgressChallenges();
            log.info("Daily maintenance: swept {} IN_PROGRESS challenge participations", challengesSwept);
        } catch (Exception ex) {
            log.error("Daily maintenance: challenge sweep failed", ex);
        }
    }
}