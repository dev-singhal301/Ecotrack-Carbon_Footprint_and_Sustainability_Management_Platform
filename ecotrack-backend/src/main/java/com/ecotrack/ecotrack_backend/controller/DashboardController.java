package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.DashboardSummaryResponse;
import com.ecotrack.ecotrack_backend.dto.FootprintTrendPointResponse;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.exception.GoalAccessDeniedException;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.CarbonFootprintService;
import com.ecotrack.ecotrack_backend.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CarbonFootprintService carbonFootprintService;
    private final UserRepository userRepository;

    public DashboardController(DashboardService dashboardService,
                               CarbonFootprintService carbonFootprintService,
                               UserRepository userRepository) {
        this.dashboardService = dashboardService;
        this.carbonFootprintService = carbonFootprintService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/dashboard/summary
     * Fetches the unified dashboard summary response for the authenticated user
     * including carbon footprint metrics, gamification stats (XP, level, rank), and goals.
     */
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = extractUser(principal);
        Long userId = user.getId();

        // 1. Log daily snapshot for historical tracking
        carbonFootprintService.logDailySnapshot(userId);

        // 2. Build and return unified dashboard summary
        DashboardSummaryResponse response = dashboardService.getDashboardSummary(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/dashboard/trend?days=30
     * GET /api/dashboard/trend?from=2026-06-01&to=2026-06-30
     * Returns daily emissions snapshots (total + per-category) for line chart trends.
     */
    @GetMapping("/trend")
    public ResponseEntity<List<FootprintTrendPointResponse>> getTrend(
            @RequestParam(name = "days", defaultValue = "30") int days,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = extractUser(principal);
        List<FootprintTrendPointResponse> trend;

        if (from != null && to != null) {
            trend = carbonFootprintService.getFootprintTrendByRange(user.getId(), from, to);
        } else {
            trend = carbonFootprintService.getFootprintTrend(user.getId(), days);
        }

        return ResponseEntity.ok(trend);
    }

    /**
     * Helper method to resolve authenticated User entity from UserDetails security principal.
     */
    private User extractUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found with email: " + principal.getUsername()));
    }

    // ── Exception Handlers ──

    @ExceptionHandler(GoalAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(GoalAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}