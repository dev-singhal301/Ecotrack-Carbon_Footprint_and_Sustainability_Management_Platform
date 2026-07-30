package com.ecotrack.ecotrack_backend.dto;

public class LeaderboardUserDto {

    private Long userId;
    private String name;
    private String userRank;
    private Integer userLevel;
    private Long monthlyXp;
    private Long rank;

    public LeaderboardUserDto(Long userId, String name, String userRank, Integer userLevel, Long monthlyXp, Long rank) {
        this.userId = userId;
        this.name = name;
        this.userRank = userRank;
        this.userLevel = userLevel;
        this.monthlyXp = monthlyXp;
        this.rank = rank;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getUserRank() { return userRank; }
    public Integer getUserLevel() { return userLevel; }
    public Long getMonthlyXp() { return monthlyXp; }
    public Long getRank() { return rank; }
}