package com.ecotrack.ecotrack_backend.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Verifies Google ID tokens sent from the Angular frontend (Google Identity
 * Services). Confirms the token is genuinely signed by Google and was issued
 * for OUR app specifically (audience check) — never trust a token's contents
 * without this check, since anyone could send an arbitrary JWT-looking string.
 */
@Component
public class GoogleTokenVerifier {

    @Value("${google.client.id}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    public void init() {
        verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    /**
     * Verifies the token and returns its payload (email, name, subject/sub id).
     * Throws IllegalArgumentException if invalid/expired/wrong audience —
     * caught by AuthController's existing exception handlers.
     */
    public GoogleIdToken.Payload verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new IllegalArgumentException("Invalid Google ID token.");
            }
            return idToken.getPayload();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Google ID token.");
        }
    }
}