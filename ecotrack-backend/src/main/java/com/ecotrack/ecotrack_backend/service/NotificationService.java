package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.NotificationResponse;
import com.ecotrack.ecotrack_backend.entity.Notification;
import com.ecotrack.ecotrack_backend.exception.NotificationAccessDeniedException;
import com.ecotrack.ecotrack_backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Owns the notifications table. Other modules (goals, challenges,
 * leaderboard) never write to it directly — they publish domain events
 * (GoalCompletedEvent, ChallengeExpiredEvent, etc.) or, for the monthly
 * recap, call createNotification directly from a scheduled job. This keeps
 * "what a notification says" in one place instead of scattered across every
 * module that can trigger one.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // REQUIRES_NEW matters here, not just as a preference: this method is
    // called from NotificationEventListener's @TransactionalEventListener
    // (phase = AFTER_COMMIT), which runs after the triggering transaction
    // has already committed. At that point Spring's synchronization for the
    // old transaction is still registered on the thread, so the default
    // REQUIRED propagation can silently "join" that finished transaction
    // instead of opening a live one — save() returns normally but nothing
    // is ever actually flushed (no exception, no generated id, no INSERT).
    // REQUIRES_NEW forces a genuine fresh transaction every time.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification createNotification(Long userId, String title, String message, String type) {
        Notification notification = new Notification(userId, title, message, type);
        return notificationRepository.saveAndFlush(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new NotificationAccessDeniedException("You do not have permission to modify this notification");
        }

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
    }
}