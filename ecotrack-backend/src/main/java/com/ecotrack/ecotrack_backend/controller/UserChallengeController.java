package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.UserChallengeResponse;
import com.ecotrack.ecotrack_backend.service.UserChallengeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/challenges")
public class UserChallengeController {

    private final UserChallengeService userChallengeService;

    public UserChallengeController(UserChallengeService userChallengeService) {
        this.userChallengeService = userChallengeService;
    }

    @GetMapping
    public ResponseEntity<List<UserChallengeResponse>> getUserChallenges(@AuthenticationPrincipal UserDetails userDetails) {
        List<UserChallengeResponse> challenges = userChallengeService.getUserChallenges(userDetails.getUsername());
        return ResponseEntity.ok(challenges);
    }

    @PostMapping("/{challengeId}/join")
    public ResponseEntity<UserChallengeResponse> joinChallenge(
            @PathVariable Long challengeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserChallengeResponse response = userChallengeService.joinChallenge(userDetails.getUsername(), challengeId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{challengeId}/claim")
    public ResponseEntity<UserChallengeResponse> claimReward(
            @PathVariable Long challengeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserChallengeResponse response = userChallengeService.claimReward(userDetails.getUsername(), challengeId);
        return ResponseEntity.ok(response);
    }
}