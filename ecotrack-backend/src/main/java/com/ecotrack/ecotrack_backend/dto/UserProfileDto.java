package com.ecotrack.ecotrack_backend.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserProfileDto {

    private Long userId;
    private String name;
    private String email;
    private String avatarUrl;
    private Integer userLevel;
    private String userRank;
    private Integer currentXp;
    private Integer nextLevelXpTarget;
    private Integer rewardPoints;
    private LocalDateTime memberSince;

    // --- 5 Active Profile Stat Metrics ---
    private long goalsPassed;
    private long goalsAccomplished; // Alias for goalsPassed
    private long activeGoals;

    private long challengesPassed;
    private long challengesAccomplished; // Alias for challengesPassed
    private long activeChallenges;

    private long totalEntries;
    private long dailyLogCount; // Alias for totalEntries

    // Legacy / Optional Stat Counts
    private long goalsFailed;
    private long challengesFailed;

    // Badge Vault (Initialized to avoid null payloads)
    private List<BadgeDto> badges = new ArrayList<>();

    public UserProfileDto() {}

    // --- Standard Getters and Setters ---
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Integer getUserLevel() { return userLevel; }
    public void setUserLevel(Integer userLevel) { this.userLevel = userLevel; }

    public String getUserRank() { return userRank; }
    public void setUserRank(String userRank) { this.userRank = userRank; }

    public Integer getCurrentXp() { return currentXp; }
    public void setCurrentXp(Integer currentXp) { this.currentXp = currentXp; }

    public Integer getNextLevelXpTarget() { return nextLevelXpTarget; }
    public void setNextLevelXpTarget(Integer nextLevelXpTarget) { this.nextLevelXpTarget = nextLevelXpTarget; }

    public Integer getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(Integer rewardPoints) { this.rewardPoints = rewardPoints; }

    public LocalDateTime getMemberSince() { return memberSince; }
    public void setMemberSince(LocalDateTime memberSince) { this.memberSince = memberSince; }

    // --- Profile Stat Getters & Setters with Dual Sync ---
    public long getGoalsPassed() { return goalsPassed; }
    public void setGoalsPassed(long goalsPassed) { 
        this.goalsPassed = goalsPassed; 
        this.goalsAccomplished = goalsPassed;
    }

    public long getGoalsAccomplished() { return goalsAccomplished; }
    public void setGoalsAccomplished(long goalsAccomplished) { 
        this.goalsAccomplished = goalsAccomplished; 
        this.goalsPassed = goalsAccomplished;
    }

    public long getActiveGoals() { return activeGoals; }
    public void setActiveGoals(long activeGoals) { this.activeGoals = activeGoals; }

    public long getChallengesPassed() { return challengesPassed; }
    public void setChallengesPassed(long challengesPassed) { 
        this.challengesPassed = challengesPassed; 
        this.challengesAccomplished = challengesPassed;
    }

    public long getChallengesAccomplished() { return challengesAccomplished; }
    public void setChallengesAccomplished(long challengesAccomplished) { 
        this.challengesAccomplished = challengesAccomplished; 
        this.challengesPassed = challengesAccomplished;
    }

    public long getActiveChallenges() { return activeChallenges; }
    public void setActiveChallenges(long activeChallenges) { this.activeChallenges = activeChallenges; }

    public long getTotalEntries() { return totalEntries; }
    public void setTotalEntries(long totalEntries) { 
        this.totalEntries = totalEntries; 
        this.dailyLogCount = totalEntries;
    }

    public long getDailyLogCount() { return dailyLogCount; }
    public void setDailyLogCount(long dailyLogCount) { 
        this.dailyLogCount = dailyLogCount; 
        this.totalEntries = dailyLogCount;
    }

    public long getGoalsFailed() { return goalsFailed; }
    public void setGoalsFailed(long goalsFailed) { this.goalsFailed = goalsFailed; }

    public long getChallengesFailed() { return challengesFailed; }
    public void setChallengesFailed(long challengesFailed) { this.challengesFailed = challengesFailed; }

    public List<BadgeDto> getBadges() { return badges; }
    public void setBadges(List<BadgeDto> badges) { 
        this.badges = badges != null ? badges : new ArrayList<>(); 
    }

    // --- Nested Badge DTO for Profile Response ---
    public static class BadgeDto {
        private Long badgeId;
        private String name;
        private String description;
        private String category;
        private String iconUrl;
        private Integer requiredXp;
        private boolean unlocked;
        private LocalDateTime awardedAt;

        public BadgeDto() {}

        public BadgeDto(Long badgeId, String name, String description, String category, String iconUrl, Integer requiredXp, boolean unlocked, LocalDateTime awardedAt) {
            this.badgeId = badgeId;
            this.name = name;
            this.description = description;
            this.category = category;
            this.iconUrl = iconUrl;
            this.requiredXp = requiredXp;
            this.unlocked = unlocked;
            this.awardedAt = awardedAt;
        }

        public Long getBadgeId() { return badgeId; }
        public void setBadgeId(Long badgeId) { this.badgeId = badgeId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getIconUrl() { return iconUrl; }
        public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

        public Integer getRequiredXp() { return requiredXp; }
        public void setRequiredXp(Integer requiredXp) { this.requiredXp = requiredXp; }

        public boolean isUnlocked() { return unlocked; }
        public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }

        public LocalDateTime getAwardedAt() { return awardedAt; }
        public void setAwardedAt(LocalDateTime awardedAt) { this.awardedAt = awardedAt; }
    }
}