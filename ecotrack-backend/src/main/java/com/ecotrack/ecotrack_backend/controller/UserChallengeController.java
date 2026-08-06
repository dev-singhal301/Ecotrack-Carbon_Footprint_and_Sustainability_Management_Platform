package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.ActivityLogRequest;
import com.ecotrack.ecotrack_backend.dto.UserChallengeResponse;
import com.ecotrack.ecotrack_backend.exception.ChallengeNotFoundException;
import com.ecotrack.ecotrack_backend.exception.DuplicateChallengeParticipationException;
import com.ecotrack.ecotrack_backend.exception.JoinWindowClosedException;
import com.ecotrack.ecotrack_backend.service.UserChallengeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-challenges")
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
     * Logs one activity event for an ACTIVITY_BASED challenge the user has
     * joined (e.g. "+1 tree planted", "5.2 km cycled"). Carbon-reduction
     * challenges reject this endpoint — their progress comes from
     * carbon_entries automatically, never a manual log.
     */
    @PostMapping("/{challengeId}/log-activity")
    public ResponseEntity<UserChallengeResponse> logActivity(
            @PathVariable Long challengeId,
            @RequestBody(required = false) @Valid ActivityLogRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserChallengeResponse response = userChallengeService.logActivity(
                userDetails.getUsername(), challengeId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Finalizes and claims XP for a challenge the user has reached the
     * target on. Idempotent — normal completion already happens
     * automatically via logActivity or the lazy carbon-period check, so
     * this exists mainly as an explicit UI action / fallback.
     */
    @PostMapping("/{challengeId}/claim")
    public ResponseEntity<UserChallengeResponse> claimReward(
            @PathVariable Long challengeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserChallengeResponse response = userChallengeService.claimReward(userDetails.getUsername(), challengeId);
        return ResponseEntity.ok(response);
    }

    // ── Exception handling (same convention as AdminController) ──

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(ChallengeNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleChallengeNotFound(ChallengeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler({DuplicateChallengeParticipationException.class, JoinWindowClosedException.class,
            IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }
}
