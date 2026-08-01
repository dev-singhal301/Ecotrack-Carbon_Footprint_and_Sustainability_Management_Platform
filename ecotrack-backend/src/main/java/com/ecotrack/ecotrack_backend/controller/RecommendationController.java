package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.RecommendationResponse;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final UserRepository userRepository;

    public RecommendationController(RecommendationService recommendationService, UserRepository userRepository) {
        this.recommendationService = recommendationService;
        this.userRepository = userRepository;
    }

    // Same auth pattern as CarbonEntryController: JWT principal name is the
    // user's email (set by JwtAuthFilter), resolved to the User entity here.
    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    @GetMapping
    public ResponseEntity<RecommendationResponse> getRecommendations(Authentication authentication) {
        User user = getCurrentUser(authentication);
        RecommendationResponse response = recommendationService.getRecommendations(user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RecommendationResponse> refreshRecommendations(Authentication authentication) {
        User user = getCurrentUser(authentication);
        RecommendationResponse response = recommendationService.refreshRecommendations(user.getId());
        return ResponseEntity.ok(response);
    }
}