package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.UserProfileDto;
import com.ecotrack.ecotrack_backend.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Backward-compatibility endpoints that address a user by numeric ID rather
 * than the authenticated principal. All "/me/..." routes for the currently
 * logged-in user live in {@link UserController} — do not re-add them here,
 * as duplicate "/me" mappings across two controllers cause a Spring
 * "Ambiguous mapping" startup failure.
 */
@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileDto> getUserProfile(@PathVariable Long userId) {
        UserProfileDto profile = userProfileService.getUserProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/{userId}/avatar")
    public ResponseEntity<Map<String, String>> updateAvatar(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        String avatarUrl = request != null ? request.get("avatarUrl") : null;
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Avatar URL cannot be empty"));
        }
        String updatedUrl = userProfileService.updateAvatar(userId, avatarUrl.trim());
        return ResponseEntity.ok(Map.of("avatarUrl", updatedUrl));
    }
}