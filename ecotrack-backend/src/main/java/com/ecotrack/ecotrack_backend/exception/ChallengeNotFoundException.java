package com.ecotrack.ecotrack_backend.exception;

public class ChallengeNotFoundException extends RuntimeException {
    public ChallengeNotFoundException(String message) {
        super(message);
    }
}