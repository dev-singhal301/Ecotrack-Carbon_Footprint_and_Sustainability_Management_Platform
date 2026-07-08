package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.AuthResponse;
import com.ecotrack.ecotrack_backend.dto.LoginRequest;
import com.ecotrack.ecotrack_backend.dto.RegisterRequest;
import com.ecotrack.ecotrack_backend.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
* REST controller for authentication.
* Receives authentication requests from the frontend
* and delegates them to AuthService.
*/
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /*
    * Registers a new user account.
    * Returns HTTP 201 Created after successful registration.
    */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /*
    * Authenticates an existing user.
    * Returns HTTP 200 OK with a JWT and user information.
    */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /*
    * Handles registration-related errors.
    * Returns HTTP 409 Conflict.
    */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    /*
    * Handles authentication failures.
    * Returns HTTP 401 Unauthorized.
    */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
    }
}