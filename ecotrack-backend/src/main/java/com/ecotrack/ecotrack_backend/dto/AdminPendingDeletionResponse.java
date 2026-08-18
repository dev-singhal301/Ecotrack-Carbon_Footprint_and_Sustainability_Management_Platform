package com.ecotrack.ecotrack_backend.dto;

import java.time.LocalDateTime;

/**
 * Row shape for the admin "pending account deletions" queue — users who hit
 * Delete Account in eco-identity and are now soft-deleted (enabled=false,
 * deletionRequestedAt set), waiting on tech support to review and run the
 * permanent purge.
 */
public class AdminPendingDeletionResponse {

    private Long userId;
    private String name;
    private String email;
    private LocalDateTime deletionRequestedAt;

    public AdminPendingDeletionResponse() {
    }

    public AdminPendingDeletionResponse(Long userId, String name, String email, LocalDateTime deletionRequestedAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.deletionRequestedAt = deletionRequestedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getDeletionRequestedAt() {
        return deletionRequestedAt;
    }

    public void setDeletionRequestedAt(LocalDateTime deletionRequestedAt) {
        this.deletionRequestedAt = deletionRequestedAt;
    }
}