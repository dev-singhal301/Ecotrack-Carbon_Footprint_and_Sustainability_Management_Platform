package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.GoalInsightsBatchResponse;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.GoalInsightService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Same auth pattern as every other controller: authentication.getName() -> email -> User -> id.
@RestController
@RequestMapping("/api/goal-insights")
public class GoalInsightController {

    private final GoalInsightService goalInsightService;
    private final UserRepository userRepository;

    public GoalInsightController(GoalInsightService goalInsightService, UserRepository userRepository) {
        this.goalInsightService = goalInsightService;
        this.userRepository = userRepository;
    }

    // Single GET, reused for both initial load and refresh — no persisted
    // table to delete-and-reinsert here, unlike /api/recommendations.
    @GetMapping
    public GoalInsightsBatchResponse getInsights(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + authentication.getName()));
        return goalInsightService.getInsights(user.getId());
    }
}