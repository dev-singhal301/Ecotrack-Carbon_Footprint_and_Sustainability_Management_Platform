package com.ecotrack.ecotrack_backend.exception;

public class GoalAccessDeniedException extends RuntimeException {
    public GoalAccessDeniedException(String message) {
        super(message);
    }
}