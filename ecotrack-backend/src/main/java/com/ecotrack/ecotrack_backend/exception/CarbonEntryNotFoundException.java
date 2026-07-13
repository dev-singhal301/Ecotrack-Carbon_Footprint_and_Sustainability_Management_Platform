package com.ecotrack.ecotrack_backend.exception;

public class CarbonEntryNotFoundException extends RuntimeException {
    public CarbonEntryNotFoundException(String message) {
        super(message);
    }
}