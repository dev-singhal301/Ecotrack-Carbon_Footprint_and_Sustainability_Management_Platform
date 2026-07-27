package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.AuthResponse;
import com.ecotrack.ecotrack_backend.dto.LoginRequest;
import com.ecotrack.ecotrack_backend.dto.RegisterRequest;
import com.ecotrack.ecotrack_backend.entity.Role;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.RoleRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.security.GoogleTokenVerifier;
import com.ecotrack.ecotrack_backend.security.JwtUtil;
import com.ecotrack.ecotrack_backend.security.GoogleTokenVerifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Handles the authentication business logic.
 * Responsible for user registration, login, and password recovery.
 * Works with repositories for database operations and delegates credential verification to Spring Security.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthService(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtUtil jwtUtil,
            GoogleTokenVerifier googleTokenVerifier) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.googleTokenVerifier = googleTokenVerifier;
    }

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

        userRepository.save(user);

        return new AuthResponse(null, user.getName(), user.getEmail(), true);
    }

   public AuthResponse login(LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user could not be reloaded: " + request.getEmail()));

        String token = jwtUtil.generateToken(user.getEmail());

        return new AuthResponse(token, user.getName(), user.getEmail(), user.getPassword() != null);
    }

    /*
     * Google OAuth2 login. Verifies the id_token against Google directly,
     * then either logs in an existing user or creates one on the fly
     * (first-time Google sign-in doubles as registration).
     *
     * Linking rule: if a user already registered normally with this email
     * (traditional password signup) and later uses "Sign in with Google"
     * with the same email, we link the accounts rather than creating a
     * duplicate — Google has already verified they own that email address.
     */
    public AuthResponse googleLogin(String idTokenString) {
        var payload = googleTokenVerifier.verify(idTokenString);

        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        User user = userRepository.findByOauthId(googleId)
                .or(() -> userRepository.findByEmail(email))
                .orElseGet(() -> {
                    Role userRole = roleRepository.findByName("ROLE_USER")
                            .orElseThrow(() -> new IllegalStateException(
                                    "Default role ROLE_USER not found. Check that schema.sql seeded the roles table."));
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setRole(userRole);
                    newUser.setEnabled(true);
                    return newUser;
                });

        user.setName(name != null ? name : user.getName());
        user.setOauthProvider("GOOGLE");
        user.setOauthId(googleId);

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getName(), user.getEmail(), user.getPassword() != null);
    }

    /*
     * Generates a temporary, secure token for password recovery.
     * Finds the user by email, assigns a UUID string, sets a strict 15-minute
     * expiration,
     * and persists the update back to the database.
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

    /*
     * Completes the password reset flow. This was previously missing entirely,
     * which is why the update never reached PostgreSQL: nothing existed to call.
     *
     * Steps: look up the user by the reset token, reject it if it doesn't exist
     * or has expired, hash the new password through the same PasswordEncoder
     * used everywhere else, save, then null out both reset fields so the token
     * cannot be reused (one-time use, per the spec's replay-attack requirement).
     */
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetPasswordToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token."));

        if (user.getTokenExpiryDate() == null || user.getTokenExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid or expired password reset token.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        user.setTokenExpiryDate(null);

        userRepository.save(user);
    }

    /*
     * Lets a Google-only user (password == null) set a local password for
     * the first time, so they gain a second way to log in going forward.
     * Deliberately separate from resetPassword — no token involved, since
     * the caller is already authenticated via JWT (see AuthController).
     * Refuses to overwrite an existing password; that's changePassword's
     * job, a different, not-yet-built feature.
     */
    public void setPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        if (user.getPassword() != null) {
            throw new IllegalStateException("Password already set for this account. Use change-password instead.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}