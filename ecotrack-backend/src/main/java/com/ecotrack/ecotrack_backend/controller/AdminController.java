package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.AdminDashboardSummaryResponse;
import com.ecotrack.ecotrack_backend.service.AdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin Portal backend. Every endpoint here requires ROLE_ADMIN — enforced
 * via @PreAuthorize, on top of SecurityConfig already requiring *any*
 * authenticated user for everything under /api/admin/**.
 *
 * User management (GET/DELETE /api/admin/users) and challenge management
 * (POST/PUT/DELETE /api/admin/challenges) are separate, later pieces of
 * work — not built here yet, per the current task's scope.
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
}