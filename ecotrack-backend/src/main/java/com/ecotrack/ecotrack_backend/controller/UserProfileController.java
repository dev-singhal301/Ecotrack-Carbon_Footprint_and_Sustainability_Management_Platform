package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.UserProfileDto;
import com.ecotrack.ecotrack_backend.service.UserProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /**
     * GET /api/users/me/profile
     * Retrieves the complete Eco-Identity profile for the currently logged-in user.
     */
    @GetMapping("/me/profile")
    public ResponseEntity<UserProfileDto> getMyProfile(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserProfileDto profile = userProfileService.getUserProfileByEmail(principal.getUsername());
        return ResponseEntity.ok(profile);
    }

    /**
     * PUT /api/users/me/name
     * Updates the username for the currently logged-in user.
     */
    @PutMapping("/me/name")
    public ResponseEntity<Map<String, String>> updateMyName(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody Map<String, String> request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String newName = request.get("name");
        if (newName == null || newName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Name cannot be empty"));
        }
        String updatedName = userProfileService.updateUsernameByEmail(principal.getUsername(), newName);
        return ResponseEntity.ok(Map.of("name", updatedName));
    }

    /**
     * PUT /api/users/me/avatar
     * Updates the avatar URL for the currently logged-in user.
     */
    @PutMapping("/me/avatar")
    public ResponseEntity<Map<String, String>> updateMyAvatar(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody Map<String, String> request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String avatarUrl = request.get("avatarUrl");
        String updatedUrl = userProfileService.updateAvatarByEmail(principal.getUsername(), avatarUrl);
        return ResponseEntity.ok(Map.of("avatarUrl", updatedUrl));
    }

    /**
     * DELETE /api/users/me
     * Permanently deletes the authenticated user's account and data.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> deleteMyAccount(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        userProfileService.deleteAccountByEmail(principal.getUsername());
        return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));
    }

    // ── Direct ID Endpoints (Backward Compatibility) ──

    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileDto> getUserProfile(@PathVariable Long userId) {
        UserProfileDto profile = userProfileService.getUserProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/{userId}/avatar")
    public ResponseEntity<Map<String, String>> updateAvatar(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        String avatarUrl = request.get("avatarUrl");
        String updatedUrl = userProfileService.updateAvatar(userId, avatarUrl);
        return ResponseEntity.ok(Map.of("avatarUrl", updatedUrl));
    }
}