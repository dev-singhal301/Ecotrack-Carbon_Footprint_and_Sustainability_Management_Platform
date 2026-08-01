package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.AdminDashboardSummaryResponse;
import com.ecotrack.ecotrack_backend.dto.AdminUserResponse;
import com.ecotrack.ecotrack_backend.dto.ChallengeRequest;
import com.ecotrack.ecotrack_backend.dto.ChallengeResponse;
import com.ecotrack.ecotrack_backend.exception.ChallengeNotFoundException;
import com.ecotrack.ecotrack_backend.exception.InvalidChallengeRequestException;
import com.ecotrack.ecotrack_backend.service.AdminService;
import com.ecotrack.ecotrack_backend.service.ChallengeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin Portal backend. Every endpoint here requires ROLE_ADMIN — enforced
 * via @PreAuthorize, on top of SecurityConfig already requiring *any*
 * authenticated user for everything under /api/admin/**.
 *
 * Challenge CRUD lives here too (not a dedicated ChallengeController)
 * because it's admin-only per the Challenge Module Design doc's API table.
 * The public /api/challenges/* endpoints (list/join/log-activity) are a
 * separate ChallengeController, not built yet — that's user-side territory.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ChallengeService challengeService;

    public AdminController(AdminService adminService, ChallengeService challengeService) {
        this.adminService = adminService;
        this.challengeService = challengeService;
    }

    // Kept from the original role-verification step — cheap way to confirm
    // "is this token an admin token at all" independent of any real data.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ping")
    public Map<String, String> ping(Authentication authentication) {
        return Map.of(
                "message", "Admin access granted",
                "authenticatedAs", authentication.getName()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dashboard-summary")
    public AdminDashboardSummaryResponse dashboardSummary() {
        return adminService.getDashboardSummary();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    // Soft-deactivation only (User.enabled = false), never a hard delete —
    // preserves the FK-linked history (carbon entries, goals, etc.) the same
    // way Challenge cancellation is soft rather than destructive.
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deactivateUser(
            @PathVariable Long id,
            Authentication authentication) {
        adminService.deactivateUser(id, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "User deactivated successfully"));
    }

    // Reverses a deactivation. PATCH (not PUT) since this only flips one
    // flag rather than replacing the whole resource — same reasoning as
    // the existing PATCH /api/notifications/{id}/read pattern.
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/users/{id}/reactivate")
    public ResponseEntity<Map<String, String>> reactivateUser(@PathVariable Long id) {
        adminService.reactivateUser(id);
        return ResponseEntity.ok(Map.of("message", "User reactivated successfully"));
    }

    // ── Challenge Management ──

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/challenges")
    public ResponseEntity<List<ChallengeResponse>> getAllChallenges() {
        return ResponseEntity.ok(challengeService.getAllChallenges());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/challenges/{challengeId}")
    public ResponseEntity<ChallengeResponse> getChallengeById(@PathVariable Long challengeId) {
        return ResponseEntity.ok(challengeService.getChallengeById(challengeId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/challenges")
    public ResponseEntity<ChallengeResponse> createChallenge(
            @Valid @RequestBody ChallengeRequest request,
            Authentication authentication) {
        ChallengeResponse response = challengeService.createChallenge(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/challenges/{challengeId}")
    public ResponseEntity<ChallengeResponse> updateChallenge(
            @PathVariable Long challengeId,
            @Valid @RequestBody ChallengeRequest request) {
        return ResponseEntity.ok(challengeService.updateChallenge(challengeId, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/challenges/{challengeId}")
    public ResponseEntity<Map<String, String>> cancelChallenge(@PathVariable Long challengeId) {
        challengeService.cancelChallenge(challengeId);
        return ResponseEntity.ok(Map.of("message", "Challenge cancelled successfully"));
    }

    // ── Exception handlers ──

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(InvalidChallengeRequestException.class)
    public ResponseEntity<Map<String, String>> handleInvalidChallengeRequest(InvalidChallengeRequestException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ChallengeNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleChallengeNotFound(ChallengeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }
}