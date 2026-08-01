package com.ecotrack.ecotrack_backend.dto;

public class UserPastHistoryDto {
    private String monthKey; // e.g. "2026-07"
    private Long totalXp;

    public UserPastHistoryDto() {}

    public UserPastHistoryDto(String monthKey, Long totalXp) {
        this.monthKey = monthKey;
        this.totalXp = totalXp;
    }

    public String getMonthKey() {
        return monthKey;
    }

    public void setMonthKey(String monthKey) {
        this.monthKey = monthKey;
    }

    public Long getTotalXp() {
        return totalXp;
    }

    public void setTotalXp(Long totalXp) {
        this.totalXp = totalXp;
    }
}