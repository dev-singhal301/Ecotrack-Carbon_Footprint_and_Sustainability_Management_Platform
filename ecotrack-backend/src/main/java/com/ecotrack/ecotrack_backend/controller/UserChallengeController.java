package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.UserChallengeResponse;
import com.ecotrack.ecotrack_backend.service.UserChallengeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-challenges")
@CrossOrigin(origins = "*")
public class UserChallengeController {

    private final UserChallengeService userChallengeService;

    public UserChallengeController(UserChallengeService userChallengeService) {
        this.userChallengeService = userChallengeService;
    }

    /**
     * Fetches all public challenges along with the authenticated user's participation status.
     */
    @GetMapping
    public ResponseEntity<List<UserChallengeResponse>> getUserChallenges(@AuthenticationPrincipal UserDetails userDetails) {
        List<UserChallengeResponse> challenges = userChallengeService.getUserChallenges(userDetails.getUsername());
        return ResponseEntity.ok(challenges);
    }

    /**
     * Joins a challenge for the authenticated user.
     */
    @PostMapping("/{challengeId}/join")
    public ResponseEntity<UserChallengeResponse> joinChallenge(
            @PathVariable Long challengeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserChallengeResponse response = userChallengeService.joinChallenge(userDetails.getUsername(), challengeId);
        return ResponseEntity.ok(response);
    }

    /**
     * Claims XP reward for a completed challenge.
     */
    @PostMapping("/{challengeId}/claim")
    public ResponseEntity<UserChallengeResponse> claimReward(
            @PathVariable Long challengeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserChallengeResponse response = userChallengeService.claimReward(userDetails.getUsername(), challengeId);
        return ResponseEntity.ok(response);
    }

    // ── Exception Handlers ──

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}