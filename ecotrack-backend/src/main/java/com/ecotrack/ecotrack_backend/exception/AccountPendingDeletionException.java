package com.ecotrack.ecotrack_backend.exception;

import org.springframework.security.authentication.DisabledException;

/**
 * Thrown when login is attempted on an account that's disabled because the
 * user requested account deletion (soft-deleted, awaiting admin purge).
 * Kept distinct from a plain DisabledException so AuthController can send
 * back reactivation instructions instead of the generic suspension message.
 */
public class AccountPendingDeletionException extends DisabledException {
    public AccountPendingDeletionException(String message) {
        super(message);
    }
}