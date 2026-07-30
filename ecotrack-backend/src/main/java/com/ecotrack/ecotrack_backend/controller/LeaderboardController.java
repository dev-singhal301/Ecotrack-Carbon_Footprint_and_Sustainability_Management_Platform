package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.LeaderboardUserDto;
import com.ecotrack.ecotrack_backend.service.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@CrossOrigin(origins = "*")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    // Spring automatically injects LeaderboardService without needing @Autowired on single constructors
    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * GET /api/leaderboard/monthly?limit=50
     * Returns top monthly users sorted by XP earned this month.
     */
    @GetMapping("/monthly")
    public ResponseEntity<List<LeaderboardUserDto>> getMonthlyLeaderboard(
            @RequestParam(defaultValue = "50") int limit) {
        List<LeaderboardUserDto> leaderboard = leaderboardService.getMonthlyLeaderboard(limit);
        return ResponseEntity.ok(leaderboard);
    }
}