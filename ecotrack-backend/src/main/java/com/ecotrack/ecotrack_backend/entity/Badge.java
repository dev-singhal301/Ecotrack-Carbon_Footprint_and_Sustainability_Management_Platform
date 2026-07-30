package com.ecotrack.ecotrack_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "badges")
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "badge_id")
    private Long badgeId;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "required_xp")
    private Integer requiredXp = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.requiredXp == null) {
            this.requiredXp = 0;
        }
    }

    public Badge() {}

    public Badge(String name, String description, String category, String iconUrl, Integer requiredXp) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.iconUrl = iconUrl;
        this.requiredXp = requiredXp;
    }

    // Getters and Setters
    public Long getBadgeId() { 
        return badgeId; 
    }
    
    public void setBadgeId(Long badgeId) { 
        this.badgeId = badgeId; 
    }

    public String getName() { 
        return name; 
    }
    
    public void setName(String name) { 
        this.name = name; 
    }

    public String getDescription() { 
        return description; 
    }
    
    public void setDescription(String description) { 
        this.description = description; 
    }

    public String getCategory() { 
        return category; 
    }
    
    public void setCategory(String category) { 
        this.category = category; 
    }

    public String getIconUrl() { 
        return iconUrl; 
    }
    
    public void setIconUrl(String iconUrl) { 
        this.iconUrl = iconUrl; 
    }

    public Integer getRequiredXp() { 
        return requiredXp; 
    }
    
    public void setRequiredXp(Integer requiredXp) { 
        this.requiredXp = requiredXp; 
    }

    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }
    
    public void setCreatedAt(LocalDateTime createdAt) { 
        this.createdAt = createdAt; 
    }
}