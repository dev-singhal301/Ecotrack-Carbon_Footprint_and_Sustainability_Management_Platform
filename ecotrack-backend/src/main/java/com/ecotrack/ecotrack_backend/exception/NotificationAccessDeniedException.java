package com.ecotrack.ecotrack_backend.exception;

public class NotificationAccessDeniedException extends RuntimeException {
    public NotificationAccessDeniedException(String message) {
        super(message);
    }
}