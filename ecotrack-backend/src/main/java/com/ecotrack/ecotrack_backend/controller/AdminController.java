package com.ecotrack.ecotrack_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Placeholder for the Admin Portal's backend. At this stage this controller
 * exists ONLY to validate that role-based authorization actually works —
 * i.e. that an authenticated ROLE_ADMIN account can reach an admin-only
 * endpoint, while an authenticated ROLE_USER account and unauthenticated
 * requests are both rejected.
 *
 * The real endpoints (GET /api/admin/users, DELETE /api/admin/users/{id},
 * POST /api/admin/challenges, etc.) are added later as part of the
 * Admin Portal & Community Challenge Engine work — deliberately not built
 * yet, per this stage's scope.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    // Method-level check is the actual authorization boundary here.
    // The class-level security filter chain (SecurityConfig) already requires
    // *any* authenticated user for everything under /api/admin/**; this
    // @PreAuthorize is what narrows it down to ROLE_ADMIN specifically.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ping")
    public Map<String, String> ping(Authentication authentication) {
        return Map.of(
                "message", "Admin access granted",
                "authenticatedAs", authentication.getName()
        );
    }
}