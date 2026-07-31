package com.ecotrack.ecotrack_backend.dto;

import java.time.LocalDateTime;

/**
 * Row shape for GET /api/admin/users — the admin user-management table.
 * Deliberately excludes password/oauthId/tokens; the admin UI never needs
 * those and there's no reason to ship them over the wire.
 */
public class AdminUserResponse {

    private Long id;
    private String name;
    private String email;
    private String role;
    private boolean enabled;
    private Integer currentXp;
    private LocalDateTime createdAt;

    public AdminUserResponse() {}

    public AdminUserResponse(Long id, String name, String email, String role,
                              boolean enabled, Integer currentXp, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
        this.currentXp = currentXp;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Integer getCurrentXp() { return currentXp; }
    public void setCurrentXp(Integer currentXp) { this.currentXp = currentXp; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}