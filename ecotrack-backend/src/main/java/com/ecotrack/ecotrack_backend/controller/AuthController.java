package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.AuthResponse;
import com.ecotrack.ecotrack_backend.dto.LoginRequest;
import com.ecotrack.ecotrack_backend.dto.RegisterRequest;
import com.ecotrack.ecotrack_backend.dto.ForgotPasswordRequest;
import com.ecotrack.ecotrack_backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

/*
 * REST entry point for authentication. Thin by design: it only converts
 * JSON <-> DTOs and delegates to AuthService. No database access, password
 * handling, or JWT logic happens here — see AuthService for that.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /*
     * POST /api/auth/register
     * Creates a new account. Returns 201 Created with no JWT — the
     * frontend is expected to redirect to the login page after this.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /*
     * POST /api/auth/login
     * Authenticates an existing account. Returns 200 OK with a JWT and
     * basic user info on success.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /*
     * POST /api/auth/forgot-password
     * Initiates password recovery. Validates the email request structure,
     * generates a time-sensitive secure token string via AuthService, 
     * and confirms tracking back to the application.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String token = authService.generateResetPasswordToken(request.getEmail());
        
        // Note for Internship Presentation: We print the token out here to verify operations locally 
        // without forcing a real production SMTP configuration setup right away.
        System.out.println("====== SYSTEM RESET TOKEN GENERATED FOR " + request.getEmail() + " ======");
        System.out.println("TOKEN VALUE: " + token);
        System.out.println("==================================================================");
        
        return ResponseEntity.ok(Map.of("message", "Password reset processing initiated successfully."));
    }

    /*
     * Maps an invalid email look up during recovery (thrown by AuthService
     * as IllegalArgumentException) to 404 Not Found instead of a raw 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ex.getMessage()));
    }

    /*
     * Maps a duplicate-email registration attempt (thrown by AuthService
     * as IllegalStateException) to 409 Conflict instead of a raw 500.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(ex.getMessage()));
    }

    /*
     * Maps a failed login (bad credentials, disabled account, etc. —
     * thrown by AuthenticationManager inside AuthService.login()) to
     * 401 Unauthorized instead of a raw 500.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("Invalid email or password"));
    }

    /*
     * Maps a @Valid failure on RegisterRequest/LoginRequest/ForgotPasswordRequest
     * (e.g. blank name, malformed email, password too short) to 400 Bad Request.
     * Collects every field error into one readable message, since a
     * single request can fail multiple constraints at once.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(message));
    }

    /*
     * Shared helper so every error response has the same shape the
     * frontend expects: { "message": "..." }, accessible via err.error.message.
     */
    private Map<String, String> errorBody(String message) {
        return Map.of("message", message);
    }
}