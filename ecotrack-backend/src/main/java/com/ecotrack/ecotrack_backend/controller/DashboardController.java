package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.DashboardSummaryResponse;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.exception.GoalAccessDeniedException;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.CarbonFootprintService;
import com.ecotrack.ecotrack_backend.service.DashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
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
     * Fetches the unified dashboard summary response for the authenticated user.
     * Automatically logs a daily historical snapshot upon request.
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