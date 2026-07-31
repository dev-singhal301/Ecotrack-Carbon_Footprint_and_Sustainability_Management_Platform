package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.AuthResponse;
import com.ecotrack.ecotrack_backend.dto.LoginRequest;
import com.ecotrack.ecotrack_backend.dto.RegisterRequest;
import com.ecotrack.ecotrack_backend.dto.ForgotPasswordRequest;
import com.ecotrack.ecotrack_backend.dto.GoogleLoginRequest;
import com.ecotrack.ecotrack_backend.dto.ResetPasswordRequest;
import com.ecotrack.ecotrack_backend.dto.SetPasswordRequest;
import com.ecotrack.ecotrack_backend.service.AuthService;
import com.ecotrack.ecotrack_backend.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;

    public AuthController(AuthService authService, EmailService emailService) {
        this.authService = authService;
        this.emailService = emailService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.googleLogin(request.getIdToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Generates a reset token and emails it as a clickable link via EmailService.
     * Deliberately swallows both "email not found" and "send failed" cases into
     * the same generic response — returning a 404 for unknown emails would let
     * this endpoint be used to check which addresses are registered.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            String token = authService.generateResetPasswordToken(request.getEmail());
            emailService.sendPasswordResetEmail(request.getEmail(), token);
        } catch (IllegalArgumentException ex) {
            System.out.println("Password reset requested for unknown email: " + request.getEmail());
        } catch (Exception ex) {
            System.out.println("Failed to send password reset email: " + ex.getMessage());
        }

        return ResponseEntity.ok(messageBody(
                "If an account exists for that email, a password reset link has been sent."));
    }

    /**
     * Completes the reset flow: validates the token via AuthService and
     * persists the new hashed password. This endpoint did not exist before —
     * it's the missing piece that was causing the "update doesn't persist" bug.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(messageBody("Password has been reset successfully. You can now log in."));
    }

    /*
     * Protected route (not permitAll) — the caller must already have a
     * valid JWT. We pull the email from the authenticated principal rather
     * than trusting a value in the request body, so a user can only ever
     * set a password on their OWN account.
     */
    @PostMapping("/set-password")
    public ResponseEntity<Map<String, String>> setPassword(@Valid @RequestBody SetPasswordRequest request,
                                                             Authentication authentication) {
        String email = authentication.getName();
        authService.setPassword(email, request.getNewPassword());
        return ResponseEntity.ok(messageBody("Password set successfully. You can now also log in with your email and password."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(messageBody(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(messageBody(ex.getMessage()));
    }

    // DisabledException is a subtype of AuthenticationException — this
    // handler is more specific, so Spring resolves it ahead of the generic
    // one below for exactly this case, without needing any reordering.
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, String>> handleDisabledException(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(messageBody("Your account has been suspended. Please contact an administrator."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(messageBody("Invalid email or password"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(messageBody(message));
    }

    // Renamed from errorBody -> messageBody: it now also wraps success
    // messages (forgot-password, reset-password), not just errors.
    private Map<String, String> messageBody(String message) {
        return Map.of("message", message != null ? message : "An unexpected error occurred.");
    }
    
}