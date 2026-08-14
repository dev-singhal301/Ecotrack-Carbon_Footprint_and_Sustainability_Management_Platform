package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.UserBadgeResponse;
import com.ecotrack.ecotrack_backend.dto.UserProfileDto;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.UserProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;
    private final UserProfileService userProfileService;

    public UserController(UserRepository userRepository, UserProfileService userProfileService) {
        this.userRepository = userRepository;
        this.userProfileService = userProfileService;
    }

    /**
     * GET /api/users/me OR /api/users/me/profile
     */
    @GetMapping({"/me", "/me/profile"})
    public ResponseEntity<UserProfileDto> getCurrentUserProfile(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserProfileDto profile = userProfileService.getUserProfileByEmail(principal.getUsername());
        return ResponseEntity.ok(profile);
    }

    /**
     * GET /api/users/me/badges
     */
    @GetMapping("/me/badges")
    public ResponseEntity<List<UserBadgeResponse>> getMyBadges(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found: " + principal.getUsername()));

        List<UserBadgeResponse> badges = userProfileService.getUserBadges(user.getId());
        return ResponseEntity.ok(badges);
    }

    /**
     * PATCH /api/users/me/name
     */
    @PatchMapping("/me/name")
    public ResponseEntity<UserProfileDto> updateName(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String newName = body.get("name");
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }

        userProfileService.updateUsernameByEmail(principal.getUsername(), newName.trim());
        UserProfileDto updatedProfile = userProfileService.getUserProfileByEmail(principal.getUsername());
        return ResponseEntity.ok(updatedProfile);
    }

    /**
     * POST /api/users/me/avatar
     */
    @PostMapping("/me/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @RequestParam("avatar") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }

        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found: " + principal.getUsername()));

        try {
            String uploadDir = "uploads/avatars/";
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = Paths.get(uploadDir + filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String avatarUrl = "/uploads/avatars/" + filename;
            userProfileService.updateAvatar(user.getId(), avatarUrl);

            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to upload file: " + e.getMessage()));
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}