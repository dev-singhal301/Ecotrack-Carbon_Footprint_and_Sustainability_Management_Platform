package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.AdminDashboardSummaryResponse;
import com.ecotrack.ecotrack_backend.dto.AdminUserResponse;
import com.ecotrack.ecotrack_backend.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin Portal backend. Every endpoint here requires ROLE_ADMIN — enforced
 * via @PreAuthorize, on top of SecurityConfig already requiring *any*
 * authenticated user for everything under /api/admin/**.
 *
 * Challenge management (POST/PUT/DELETE /api/admin/challenges) is a
 * separate, later piece of work — not built here yet, per the current
 * task's scope.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
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

    // ── Exception handlers ──

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }
}