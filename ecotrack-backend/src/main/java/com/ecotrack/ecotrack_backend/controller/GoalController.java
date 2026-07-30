package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.GoalRequest;
import com.ecotrack.ecotrack_backend.dto.GoalResponse;
import com.ecotrack.ecotrack_backend.dto.GoalProgressResponse;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.GoalService;
import com.ecotrack.ecotrack_backend.exception.GoalAccessDeniedException;
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
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;
    private final UserRepository userRepository;

    public GoalController(GoalService goalService, UserRepository userRepository) {
        this.goalService = goalService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<GoalResponse> createGoal(
            @Valid @RequestBody GoalRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = extractUserId(principal);
        GoalResponse response = goalService.createGoal(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> getGoals(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) String status) {
        Long userId = extractUserId(principal);
        List<GoalResponse> goals = (status != null)
                ? goalService.getGoalsForUserByStatus(userId, status)
                : goalService.getGoalsForUser(userId);
        return ResponseEntity.ok(goals);
    }

    @GetMapping("/{goalId}")
    public ResponseEntity<GoalResponse> getGoalById(
            @PathVariable Long goalId,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = extractUserId(principal);
        return ResponseEntity.ok(goalService.getGoalById(userId, goalId));
    }

    @PutMapping("/{goalId}")
    public ResponseEntity<GoalResponse> updateGoal(
            @PathVariable Long goalId,
            @Valid @RequestBody GoalRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = extractUserId(principal);
        return ResponseEntity.ok(goalService.updateGoal(userId, goalId, request));
    }

    @DeleteMapping("/{goalId}")
    public ResponseEntity<Map<String, String>> deleteGoal(
            @PathVariable Long goalId,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = extractUserId(principal);
        goalService.deleteGoal(userId, goalId);
        return ResponseEntity.ok(Map.of("message", "Goal deleted successfully"));
    }

    @GetMapping("/{goalId}/progress")
    public ResponseEntity<GoalProgressResponse> getProgress(
            @PathVariable Long goalId,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = extractUserId(principal);
        return ResponseEntity.ok(goalService.calculateProgress(userId, goalId));
    }

    // ── Gamification: Explicit Goal Completion & Reward Trigger ──
    @PostMapping("/{goalId}/complete")
    public ResponseEntity<Map<String, String>> completeGoal(
            @PathVariable Long goalId,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = extractUserId(principal);
        goalService.completeGoal(userId, goalId);
        return ResponseEntity.ok(Map.of("message", "Goal completed successfully! XP and reward points awarded."));
    }

    // ── Exception Handlers ──

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(GoalAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(GoalAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    private Long extractUserId(UserDetails principal) {
        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found: " + principal.getUsername()));
        return user.getId();
    }
}