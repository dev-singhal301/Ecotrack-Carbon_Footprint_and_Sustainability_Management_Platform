package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.LeaderboardUserDto;
import com.ecotrack.ecotrack_backend.dto.UserPastHistoryDto;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@CrossOrigin(origins = "*")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final UserRepository userRepository;

    public LeaderboardController(LeaderboardService leaderboardService, UserRepository userRepository) {
        this.leaderboardService = leaderboardService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/leaderboard/all-time?limit=50
     * Returns top users overall, sorted by total XP, level, and rank tier.
     */
    @GetMapping("/all-time")
    public ResponseEntity<List<LeaderboardUserDto>> getAllTimeLeaderboard(
            @RequestParam(defaultValue = "50") int limit) {
        List<LeaderboardUserDto> leaderboard = leaderboardService.getAllTimeLeaderboard(limit);
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * GET /api/leaderboard/monthly?limit=50
     * Returns top monthly users sorted by XP earned during the current month.
     */
    @GetMapping("/monthly")
    public ResponseEntity<List<LeaderboardUserDto>> getMonthlyLeaderboard(
            @RequestParam(defaultValue = "50") int limit) {
        List<LeaderboardUserDto> leaderboard = leaderboardService.getMonthlyLeaderboard(limit);
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * GET /api/leaderboard/previous-month?limit=3
     * Returns top users from the previous calendar month (used for "Last Month's Champions" card).
     */
    @GetMapping("/previous-month")
    public ResponseEntity<List<LeaderboardUserDto>> getPreviousMonthLeaderboard(
            @RequestParam(defaultValue = "3") int limit) {
        List<LeaderboardUserDto> leaderboard = leaderboardService.getPreviousMonthLeaderboard(limit);
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * GET /api/leaderboard/user-history?limit=6
     * Returns the authenticated user's past monthly XP earnings history.
     */
    @GetMapping("/user-history")
    public ResponseEntity<List<UserPastHistoryDto>> getUserHistory(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "6") int limit) {
        Long userId = extractUserId(principal);
        List<UserPastHistoryDto> history = leaderboardService.getUserPastHistory(userId, limit);
        return ResponseEntity.ok(history);
    }

    /**
     * GET /api/leaderboard/user-rank
     * Returns the current authenticated user's position, total XP, level, and rank badge.
     */
    @GetMapping("/user-rank")
    public ResponseEntity<LeaderboardUserDto> getUserRank(
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = extractUserId(principal);
        LeaderboardUserDto userRank = leaderboardService.getUserRankPosition(userId);
        return ResponseEntity.ok(userRank);
    }

    private Long extractUserId(UserDetails principal) {
        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found: " + principal.getUsername()));
        return user.getId();
    }
}