package com.ecotrack.ecotrack_backend.exception;

/**
 * Thrown when a user tries to join a challenge they've already joined.
 * Maps to 409 Conflict — see Challenge Module Design doc §2.2.
 */
public class DuplicateChallengeParticipationException extends RuntimeException {
    public DuplicateChallengeParticipationException(String message) {
        super(message);
    }
}
