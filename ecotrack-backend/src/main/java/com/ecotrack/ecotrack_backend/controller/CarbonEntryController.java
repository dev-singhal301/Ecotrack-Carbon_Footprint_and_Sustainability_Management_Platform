package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.CarbonEntryRequest;
import com.ecotrack.ecotrack_backend.dto.CarbonEntryResponse;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.exception.CarbonEntryNotFoundException;
import com.ecotrack.ecotrack_backend.exception.InvalidCategoryException;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.CarbonEntryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/carbon-entries")
@CrossOrigin(origins = "*")
public class CarbonEntryController {

    private final CarbonEntryService carbonEntryService;
    private final UserRepository userRepository;

    public CarbonEntryController(CarbonEntryService carbonEntryService, UserRepository userRepository) {
        this.carbonEntryService = carbonEntryService;
        this.userRepository = userRepository;
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    @PostMapping
    public ResponseEntity<CarbonEntryResponse> createEntry(@Valid @RequestBody CarbonEntryRequest request,
                                                           Authentication authentication) {
        User user = getCurrentUser(authentication);
        CarbonEntryResponse response = carbonEntryService.createEntry(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CarbonEntryResponse>> getHistory(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            Authentication authentication) {
        User user = getCurrentUser(authentication);
        List<CarbonEntryResponse> history = carbonEntryService.getHistory(user.getId(), category, from, to);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CarbonEntryResponse> getEntry(@PathVariable Long id, Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(carbonEntryService.getEntryById(user.getId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(@PathVariable Long id, Authentication authentication) {
        User user = getCurrentUser(authentication);
        carbonEntryService.deleteEntry(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Exception Handlers ──

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(InvalidCategoryException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCategory(InvalidCategoryException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(CarbonEntryNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(CarbonEntryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }
}