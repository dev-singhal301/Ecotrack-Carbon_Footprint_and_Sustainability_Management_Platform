package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.AuthResponse;
import com.ecotrack.ecotrack_backend.dto.LoginRequest;
import com.ecotrack.ecotrack_backend.dto.RegisterRequest;
import com.ecotrack.ecotrack_backend.entity.Role;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.RoleRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Handles the authentication business logic.
 * Responsible for user registration and login.
 * Works with repositories for database operations and delegates credential verification to Spring Security.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    /*
     * Injects all required dependencies used for authentication.
     * Spring automatically provides these beans at runtime.
     */
    public AuthService(UserRepository userRepository,
                        RoleRepository roleRepository,
                        PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager,
                        JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    /*
     * Registers a new user.
     * Checks for duplicate emails, assigns the default role, hashes the password, and saves the user.
     * Registration does not generate a JWT.
     * Users must log in separately.
     */
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email is already registered: " + request.getEmail());
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException(
                        "Default role ROLE_USER not found. Check that schema.sql seeded the roles table."));

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(userRole);
        user.setEnabled(true);
        // OAuth fields remain null for email/password registration.
        // They will be used later for Google OAuth login.

        userRepository.save(user);

        // Registration is complete.
        // The user must log in to receive a JWT.
        return new AuthResponse(null, user.getName(), user.getEmail());
    }

    /*
     * Authenticates an existing user.
     * Spring Security verifies the user's credentials.
     * If authentication succeeds, a JWT is generated
     * and returned to the client.
     */
    public AuthResponse login(LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // Authentication was successful.
        // Retrieve user details to build the response.
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user could not be reloaded: " + request.getEmail()));

        String token = jwtUtil.generateToken(user.getEmail());

        return new AuthResponse(token, user.getName(), user.getEmail());
    }

    /*
     * Generates a temporary, secure token for password recovery.
     * Finds the user by email, assigns a UUID string, sets a strict 15-minute expiration,
     * and persists the update back to MySQL.
     */
    public String generateResetPasswordToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found with this email address."));

        String token = UUID.randomUUID().toString();
        user.setResetPasswordToken(token);
        user.setTokenExpiryDate(LocalDateTime.now().plusMinutes(15));

        userRepository.save(user);
        return token;
    }
}