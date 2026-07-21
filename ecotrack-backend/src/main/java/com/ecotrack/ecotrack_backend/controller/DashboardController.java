package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.*;
import com.ecotrack.ecotrack_backend.entity.Goal;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.exception.GoalAccessDeniedException;
import com.ecotrack.ecotrack_backend.repository.CarbonEntryRepository;
import com.ecotrack.ecotrack_backend.repository.GoalRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.CarbonFootprintService;
import com.ecotrack.ecotrack_backend.service.GoalService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final int RECENT_ACTIVITY_LIMIT = 5;
    private static final BigDecimal NEARING_COMPLETION_THRESHOLD = BigDecimal.valueOf(80);
    private static final long NEARING_DEADLINE_DAYS = 3;

    private final CarbonFootprintService carbonFootprintService;
    private final GoalService goalService;
    private final GoalRepository goalRepository;
    private final CarbonEntryRepository carbonEntryRepository;
    private final UserRepository userRepository;

    public DashboardController(CarbonFootprintService carbonFootprintService,
                                GoalService goalService,
                                GoalRepository goalRepository,
                                CarbonEntryRepository carbonEntryRepository,
                                UserRepository userRepository) {
        this.carbonFootprintService = carbonFootprintService;
        this.goalService = goalService;
        this.goalRepository = goalRepository;
        this.carbonEntryRepository = carbonEntryRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary(@AuthenticationPrincipal UserDetails principal) {
        Long userId = extractUserId(principal);
        LocalDate today = LocalDate.now();

        FootprintOverviewResponse overview = carbonFootprintService.getOverview(userId);
        FootprintTrendResponse trend = carbonFootprintService.getTrend(userId);

        // Recalculate every active goal's progress on every dashboard load, so
        // the numbers shown are always current — not just whatever was last
        // computed by a separate call to GET /api/goals/{id}/progress.
        // Trade-off (kept deliberately, per product decision): this makes the
        // dashboard endpoint do writes — it can flip a goal to COMPLETED or
        // EXPIRED as a side effect of a GET request.
        List<Goal> activeGoals = goalRepository.findByUserIdAndStatus(userId, "ACTIVE");
        List<GoalHighlightResponse> nearing = new ArrayList<>();
        for (Goal goal : activeGoals) {
            GoalProgressResponse progress = goalService.calculateProgress(userId, goal.getGoalId());
            long daysRemaining = ChronoUnit.DAYS.between(today, goal.getEndDate());

            if (progress.isCompleted() || daysRemaining < 0) {
                continue; // already finished, or expired and no longer actionable
            }
            boolean nearingCompletion = progress.getCompletionPercentage().compareTo(NEARING_COMPLETION_THRESHOLD) >= 0;
            boolean nearingDeadline = daysRemaining <= NEARING_DEADLINE_DAYS;
            if (nearingCompletion || nearingDeadline) {
                nearing.add(new GoalHighlightResponse(goal.getGoalId(), goal.getGoalName(),
                        goal.getGoalType(), progress.getCompletionPercentage(), daysRemaining));
            }
        }

        // Re-query counts fresh after the recalculation above, since calculateProgress()
        // may have just flipped some goals to COMPLETED or EXPIRED.
        GoalSnapshotResponse goalSnapshot = new GoalSnapshotResponse(
                goalRepository.findByUserIdAndStatus(userId, "ACTIVE").size(),
                goalRepository.findByUserIdAndStatus(userId, "COMPLETED").size());

        Pageable limit = PageRequest.of(0, RECENT_ACTIVITY_LIMIT);
        List<RecentActivityResponse> recentActivity = carbonEntryRepository
                .findByUserIdOrderByActivityDateDescEntryIdDesc(userId, limit).stream()
                .map(e -> new RecentActivityResponse(e.getEntryId(), e.getCategory(), e.getAmount(),
                        e.getUnit(), e.getCarbonEmission(), e.getActivityDate()))
                .collect(Collectors.toList());

        carbonFootprintService.logDailySnapshot(userId); // historical log only

        DashboardSummaryResponse response = new DashboardSummaryResponse(
                overview, trend, goalSnapshot, nearing, recentActivity);
        return ResponseEntity.ok(response);
    }

    private Long extractUserId(UserDetails principal) {
        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found: " + principal.getUsername()));
        return user.getId();
    }

    // ── Exception handlers — same shape as GoalController's ──

    @ExceptionHandler(GoalAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(GoalAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}