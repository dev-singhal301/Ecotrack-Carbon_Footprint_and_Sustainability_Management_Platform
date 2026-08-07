package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.LeaderboardUserDto;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Notifies every ranked user of where they landed on last month's
 * leaderboard. Runs at 00:15 on the 1st of each month — five minutes after
 * DailyMaintenanceScheduler, so any goal/challenge that completed right at
 * month-end has already been settled and its XP is reflected here.
 *
 * Reuses LeaderboardService.getPreviousMonthLeaderboard(), which already
 * backs the "Last Month's Champions" card — passing the full user count as
 * the limit so every ranked user is included, not just the top of the page.
 */
@Component
public class LeaderboardNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardNotificationScheduler.class);

    private final LeaderboardService leaderboardService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public LeaderboardNotificationScheduler(LeaderboardService leaderboardService,
                                             NotificationService notificationService,
                                             UserRepository userRepository) {
        this.leaderboardService = leaderboardService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Scheduled(cron = "0 15 0 1 * *")
    public void notifyPreviousMonthRanks() {
        int allUsers = (int) userRepository.count();
        if (allUsers == 0) {
            return;
        }

        List<LeaderboardUserDto> previousMonth = leaderboardService.getPreviousMonthLeaderboard(allUsers);

        for (LeaderboardUserDto entry : previousMonth) {
            String message = "You finished #" + entry.getRank() + " on last month's leaderboard with "
                    + entry.getMonthlyXp() + " XP.";
            notificationService.createNotification(
                    entry.getUserId(),
                    "Last month's leaderboard results",
                    message,
                    "LEADERBOARD_MONTHLY");
        }

        log.info("Monthly leaderboard notification: notified {} users", previousMonth.size());
    }
}