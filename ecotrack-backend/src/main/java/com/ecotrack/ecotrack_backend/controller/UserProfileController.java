package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.UserProfileDto;
import com.ecotrack.ecotrack_backend.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        String avatarUrl = request.get("avatarUrl");
        String updatedUrl = userProfileService.updateAvatar(userId, avatarUrl);
        return ResponseEntity.ok(Map.of("avatarUrl", updatedUrl));
    }
}