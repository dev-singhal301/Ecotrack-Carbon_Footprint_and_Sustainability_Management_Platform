package com.ecotrack.ecotrack_backend.exception;

/**
 * Thrown when a user tries to join a challenge outside the allowed window
 * (before the challenge has started, or more than
 * UserChallengeService.JOIN_WINDOW_DAYS after its start date). Maps to 409
 * Conflict — see Challenge Module Design doc §2.2 / §5.
 */
public class JoinWindowClosedException extends RuntimeException {
    public JoinWindowClosedException(String message) {
        super(message);
    }
}
