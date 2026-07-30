package com.ecotrack.ecotrack_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_badges", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "badge_id"})
})
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_badge_id")
    private Long userBadgeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "badge_id", nullable = false)
    private Long badgeId;

    @Column(name = "awarded_at", nullable = false, updatable = false)
    private LocalDateTime awardedAt;

    @PrePersist
    protected void onCreate() {
        if (this.awardedAt == null) {
            this.awardedAt = LocalDateTime.now();
        }
    }

    public UserBadge() {}

    public UserBadge(Long userId, Long badgeId) {
        this.userId = userId;
        this.badgeId = badgeId;
        this.awardedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getUserBadgeId() { 
        return userBadgeId; 
    }
    
    public void setUserBadgeId(Long userBadgeId) { 
        this.userBadgeId = userBadgeId; 
    }

    public Long getUserId() { 
        return userId; 
    }
    
    public void setUserId(Long userId) { 
        this.userId = userId; 
    }

    public Long getBadgeId() { 
        return badgeId; 
    }
    
    public void setBadgeId(Long badgeId) { 
        this.badgeId = badgeId; 
    }

    public LocalDateTime getAwardedAt() { 
        return awardedAt; 
    }
    
    public void setAwardedAt(LocalDateTime awardedAt) { 
        this.awardedAt = awardedAt; 
    }
}