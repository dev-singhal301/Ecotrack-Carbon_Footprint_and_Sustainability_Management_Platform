package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.NotificationResponse;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.exception.NotificationAccessDeniedException;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = extractUserId(principal);
        return ResponseEntity.ok(notificationService.getNotificationsForUser(userId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = extractUserId(principal);
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = extractUserId(principal);
        notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = extractUserId(principal);
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    @ExceptionHandler(NotificationAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(NotificationAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    private Long extractUserId(UserDetails principal) {
        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found: " + principal.getUsername()));
        return user.getId();
    }
}