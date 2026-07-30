package com.ecotrack.ecotrack_backend.dto;

public class LeaderboardUserDto {

    private Long userId;
    private String name;
    private String userRank;
    private Integer userLevel;
    private Long monthlyXp;
    private Long rank;

    public LeaderboardUserDto() {}

    public LeaderboardUserDto(Long userId, String name, String userRank, Integer userLevel, Long monthlyXp, Long rank) {
        this.userId = userId;
        this.name = name;
        this.userRank = userRank;
        this.userLevel = userLevel;
        this.monthlyXp = monthlyXp;
        this.rank = rank;
    }

    // Getters and Setters
    public Long getUserId() { 
        return userId; 
    }

    public void setUserId(Long userId) { 
        this.userId = userId; 
    }

    public String getName() { 
        return name; 
    }

    public void setName(String name) { 
        this.name = name; 
    }

    public String getUserRank() { 
        return userRank; 
    }

    public void setUserRank(String userRank) { 
        this.userRank = userRank; 
    }

    public Integer getUserLevel() { 
        return userLevel; 
    }

    public void setUserLevel(Integer userLevel) { 
        this.userLevel = userLevel; 
    }

    public Long getMonthlyXp() { 
        return monthlyXp; 
    }

    public void setMonthlyXp(Long monthlyXp) { 
        this.monthlyXp = monthlyXp; 
    }

    public Long getRank() { 
        return rank; 
    }

    public void setRank(Long rank) { 
        this.rank = rank; 
    }
}