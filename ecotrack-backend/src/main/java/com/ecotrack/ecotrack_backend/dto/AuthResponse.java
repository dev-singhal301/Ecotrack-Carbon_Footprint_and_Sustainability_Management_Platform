package com.ecotrack.ecotrack_backend.dto;

public class AuthResponse {
    private String token;
    private String name;
    private String email;
    private boolean hasPassword;
    private String role; // e.g. "ROLE_ADMIN" or "ROLE_USER" — lets the frontend redirect/guard correctly

    public AuthResponse() {}

    public AuthResponse(String token, String name, String email, boolean hasPassword, String role) {
        this.token = token;
        this.name = name;
        this.email = email;
        this.hasPassword = hasPassword;
        this.role = role;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isHasPassword() { return hasPassword; }
    public void setHasPassword(boolean hasPassword) { this.hasPassword = hasPassword; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}