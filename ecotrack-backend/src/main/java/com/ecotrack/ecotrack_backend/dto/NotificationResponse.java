package com.ecotrack.ecotrack_backend.dto;

import com.ecotrack.ecotrack_backend.entity.Notification;

import java.time.LocalDateTime;

public class NotificationResponse {

    private Long notificationId;
    private String title;
    private String message;
    private String type;
    private boolean isRead;
    private LocalDateTime createdAt;

    public NotificationResponse() {}

    public static NotificationResponse fromEntity(Notification notification) {
        NotificationResponse dto = new NotificationResponse();
        dto.notificationId = notification.getNotificationId();
        dto.title = notification.getTitle();
        dto.message = notification.getMessage();
        dto.type = notification.getType();
        dto.isRead = Boolean.TRUE.equals(notification.getIsRead());
        dto.createdAt = notification.getCreatedAt();
        return dto;
    }

    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}