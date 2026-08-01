package com.ecotrack.ecotrack_backend.exception;

/**
 * Thrown for challenge business-rule validation failures (bad date range,
 * invalid challenge/tracking type combination, etc.).
 *
 * Deliberately NOT IllegalArgumentException — AdminController already uses
 * that to mean "not found" -> 404 (see AdminService.deactivateUser/
 * reactivateUser). Reusing it here would either collide with that handler
 * or, if allowed to slip through, mislabel a validation error as a 404.
 * This maps to 400 Bad Request instead.
 */
public class InvalidChallengeRequestException extends RuntimeException {
    public InvalidChallengeRequestException(String message) {
        super(message);
    }
}