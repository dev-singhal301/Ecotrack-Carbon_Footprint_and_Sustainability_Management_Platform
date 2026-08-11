package com.ecotrack.ecotrack_backend.dto;

import com.ecotrack.ecotrack_backend.entity.UserChallenge;

import java.time.LocalDateTime;

/**
 * Minimal read-only projection of a UserChallenge for the admin dashboard's
 * participation analytics (current in-progress count, activity trend,
 * resolved completion rate trend). Deliberately does not nest the full
 * ChallengeResponse or user-facing fields (loggedToday, currentValue, etc.)
 * the way UserChallengeResponse does — the dashboard only needs status +
 * timestamps, aggregated client-side.
 *
 * Note: completedAt also holds the resolution timestamp for EXPIRED
 * participations, not just COMPLETED ones — see UserChallenge.completedAt's
 * doc comment.
 */
public class AdminParticipationResponse {

    private Long userChallengeId;
    private Long challengeId;
    private String status;
    private LocalDateTime joinedAt;
    private LocalDateTime completedAt;

    public AdminParticipationResponse() {}

    public static AdminParticipationResponse fromEntity(UserChallenge userChallenge) {
        AdminParticipationResponse response = new AdminParticipationResponse();
        response.userChallengeId = userChallenge.getId();
        response.challengeId = userChallenge.getChallenge() != null ? userChallenge.getChallenge().getChallengeId() : null;
        response.status = userChallenge.getStatus() != null ? userChallenge.getStatus().name() : null;
        response.joinedAt = userChallenge.getJoinedAt();
        response.completedAt = userChallenge.getCompletedAt();
        return response;
    }

    public Long getUserChallengeId() { return userChallengeId; }
    public Long getChallengeId() { return challengeId; }
    public String getStatus() { return status; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
