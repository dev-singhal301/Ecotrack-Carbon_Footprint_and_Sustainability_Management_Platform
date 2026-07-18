package com.ecotrack.ecotrack_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SetPasswordRequest {

    @NotBlank(message = "newPassword is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String newPassword;

    public SetPasswordRequest() {}

    public SetPasswordRequest(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}