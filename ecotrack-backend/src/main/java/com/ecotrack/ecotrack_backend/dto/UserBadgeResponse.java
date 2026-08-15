package com.ecotrack.ecotrack_backend.dto;

import java.time.LocalDateTime;

public class UserBadgeResponse {

    private Long badgeId;
    private String name;
    private String description;
    private String category;
    private String iconUrl;
    private Integer requiredXp;
    private Integer requiredLevel;
    private boolean unlocked;
    private LocalDateTime awardedAt;

    public UserBadgeResponse() {}

    // --- 6-argument Constructor (Required for JPQL Constructor Projections) ---
    public UserBadgeResponse(Long badgeId, String name, String description, String category, 
                             String iconUrl, LocalDateTime awardedAt) {
        this.badgeId = badgeId;
        this.name = name;
        this.description = description;
        this.category = category;
        this.iconUrl = iconUrl;
        this.requiredXp = 0;
        this.unlocked = true;
        this.awardedAt = awardedAt;
    }

    // --- 8-argument Constructor (Full Details) ---
    public UserBadgeResponse(Long badgeId, String name, String description, String category, 
                             String iconUrl, Integer requiredXp, boolean unlocked, LocalDateTime awardedAt) {
        this.badgeId = badgeId;
        this.name = name;
        this.description = description;
        this.category = category;
        this.iconUrl = iconUrl;
        this.requiredXp = requiredXp;
        this.unlocked = unlocked;
        this.awardedAt = awardedAt;
    }

    // --- Getters and Setters ---
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

    // --- Added missing Getter and Setter for requiredLevel ---
    public Integer getRequiredLevel() {
        return requiredLevel;
    }

    public void setRequiredLevel(Integer requiredLevel) {
        this.requiredLevel = requiredLevel;
    }

    public boolean isUnlocked() { 
        return unlocked; 
    }

    public void setUnlocked(boolean unlocked) { 
        this.unlocked = unlocked; 
    }

    public LocalDateTime getAwardedAt() { 
        return awardedAt; 
    }

    public void setAwardedAt(LocalDateTime awardedAt) { 
        this.awardedAt = awardedAt; 
    }
}