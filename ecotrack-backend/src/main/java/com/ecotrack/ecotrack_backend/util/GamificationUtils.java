package com.ecotrack.ecotrack_backend.util;

import com.ecotrack.ecotrack_backend.dto.UserBadgeResponse;
import com.ecotrack.ecotrack_backend.entity.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class GamificationUtils {

    public static final int MAX_LEVEL = 50;
    
    // Precomputed arrays for O(1) lookups instead of calculating loops repeatedly
    private static final int[] LEVEL_TOTAL_XP = new int[MAX_LEVEL + 1];
    private static final int[] LEVEL_XP_REQ = new int[MAX_LEVEL + 1];

    static {
        LEVEL_TOTAL_XP[1] = 0;
        LEVEL_XP_REQ[1] = 50; // XP needed to go from Level 1 to 2

        for (int i = 2; i <= MAX_LEVEL; i++) {
            // Total cumulative XP needed to REACH level i
            LEVEL_TOTAL_XP[i] = LEVEL_TOTAL_XP[i - 1] + LEVEL_XP_REQ[i - 1];

            if (i == MAX_LEVEL) {
                LEVEL_XP_REQ[i] = 0; // Max level reached
                break;
            }

            // Calculate the XP required to complete level i (i.e., go from i to i+1)
            int prevReq = LEVEL_XP_REQ[i - 1];
            if (i < 6) { // Levels 1->6: doubling
                LEVEL_XP_REQ[i] = prevReq * 2;
            } else if (i < 16) { // Levels 6->15: 1.3x multiplier
                LEVEL_XP_REQ[i] = (int) Math.round(prevReq * 1.3);
            } else if (i < 26) { // Levels 16->25: 1.2x multiplier
                LEVEL_XP_REQ[i] = (int) Math.round(prevReq * 1.2);
            } else if (i < 36) { // Levels 26->35: 1.15x multiplier
                LEVEL_XP_REQ[i] = (int) Math.round(prevReq * 1.15);
            } else { // Levels 36->50: 1.1x multiplier
                LEVEL_XP_REQ[i] = (int) Math.round(prevReq * 1.1);
            }
        }
    }

    /**
     * Represents a Title Milestone with its associated level requirement, badge name, and icon.
     */
    public static class MilestoneInfo {
        private final int requiredLevel;
        private final String title;
        private final String iconUrl;

        public MilestoneInfo(int requiredLevel, String title, String iconUrl) {
            this.requiredLevel = requiredLevel;
            this.title = title;
            this.iconUrl = iconUrl;
        }

        public int getRequiredLevel() { return requiredLevel; }
        public String getTitle() { return title; }
        public String getIconUrl() { return iconUrl; }
    }

    // Master list of all 10 Title Milestones
    public static final List<MilestoneInfo> TITLE_MILESTONES = List.of(
        new MilestoneInfo(1,  "Novice Eco",   "/badges/ms_eco_1.avif"),
        new MilestoneInfo(2,  "Eco Scout",    "/badges/ms_eco_2.avif"),
        new MilestoneInfo(3,  "Eco Explorer", "/badges/ms_eco_3.avif"),
        new MilestoneInfo(5,  "Eco Pioneer",  "/badges/ms_eco_4.avif"),
        new MilestoneInfo(8,  "Eco Guardian", "/badges/ms_eco_5.avif"),
        new MilestoneInfo(11, "Eco Sentinel", "/badges/ms_eco_6.avif"),
        new MilestoneInfo(15, "Eco Champion", "/badges/ms_eco_7.avif"),
        new MilestoneInfo(20, "Eco Master",   "/badges/ms_eco_8.avif"),
        new MilestoneInfo(25, "Eco Hero",     "/badges/ms_eco_9.avif"),
        new MilestoneInfo(50, "Mythic Eco",   "/badges/ms_eco_10.avif")
    );

    /**
     * Returns the user's level based on the non-linear XP curve.
     */
    public static int getLevel(User user) {
        int xp = (user != null && user.getCurrentXp() != null) ? user.getCurrentXp() : 0;
        
        // Find the highest level threshold the user has surpassed
        for (int i = MAX_LEVEL; i >= 1; i--) {
            if (xp >= LEVEL_TOTAL_XP[i]) {
                return i;
            }
        }
        return 1;
    }

    /**
     * Returns the cumulative lifetime XP required to unlock the *next* level.
     */
    public static int getNextLevelXpTarget(User user) {
        int currentLevel = getLevel(user);
        if (currentLevel >= MAX_LEVEL) {
            return LEVEL_TOTAL_XP[MAX_LEVEL]; // Target stays capped at 50's threshold
        }
        return LEVEL_TOTAL_XP[currentLevel + 1];
    }

    /**
     * NEW: How much relative XP the user has earned *since hitting their current level*.
     */
    public static int getCurrentLevelXpProgress(User user) {
        int currentLevel = getLevel(user);
        int xp = (user != null && user.getCurrentXp() != null) ? user.getCurrentXp() : 0;
        return Math.max(0, xp - LEVEL_TOTAL_XP[currentLevel]);
    }

    /**
     * NEW: The isolated XP "cost" to traverse from the current level to the next.
     */
    public static int getXpForNextLevel(User user) {
        int currentLevel = getLevel(user);
        if (currentLevel >= MAX_LEVEL) {
            return 0; // Max level reached
        }
        return LEVEL_XP_REQ[currentLevel];
    }

    /**
     * Returns the eco-rank title matching the user's level.
     */
    public static String getRankTitle(User user) {
        int level = getLevel(user);
        String currentTitle = "Novice Eco";
        for (MilestoneInfo milestone : TITLE_MILESTONES) {
            if (level >= milestone.getRequiredLevel()) {
                currentTitle = milestone.getTitle();
            } else {
                break;
            }
        }
        return currentTitle;
    }

    /**
     * Returns only the milestone badges earned based on the user's current level.
     */
    public static List<UserBadgeResponse> getEligibleMilestoneBadges(User user) {
        int level = getLevel(user);
        List<UserBadgeResponse> earnedMilestones = new ArrayList<>();

        for (int i = 0; i < TITLE_MILESTONES.size(); i++) {
            MilestoneInfo m = TITLE_MILESTONES.get(i);
            if (level >= m.getRequiredLevel()) {
                UserBadgeResponse badge = new UserBadgeResponse();
                badge.setBadgeId(100L + (i + 1)); // Milestone Badge IDs: 101 to 110
                badge.setName(m.getTitle());
                badge.setDescription("Reached " + m.getTitle() + " status at Level " + m.getRequiredLevel());
                badge.setCategory("MILESTONE");
                badge.setIconUrl(m.getIconUrl());
                // Required XP is the cumulative total XP at the start of that level
                badge.setRequiredXp(LEVEL_TOTAL_XP[m.getRequiredLevel()]); 
                badge.setRequiredLevel(m.getRequiredLevel());
                badge.setUnlocked(true);
                badge.setAwardedAt(user != null ? user.getCreatedAt() : null);

                earnedMilestones.add(badge);
            }
        }
        return earnedMilestones;
    }

    /**
     * Calculates carbon saved compared to an average baseline.
     */
    public static BigDecimal calculateCarbonSaved(BigDecimal totalEmissions, BigDecimal baselineEmissions) {
        if (baselineEmissions == null || baselineEmissions.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal saved = baselineEmissions.subtract(totalEmissions != null ? totalEmissions : BigDecimal.ZERO);
        return saved.compareTo(BigDecimal.ZERO) > 0 ? saved : BigDecimal.ZERO;
    }

    /**
     * Calculates savings percentage vs baseline.
     */
    public static Double calculateSavingsPercentage(BigDecimal totalEmissions, BigDecimal baselineEmissions) {
        if (baselineEmissions == null || baselineEmissions.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        BigDecimal saved = calculateCarbonSaved(totalEmissions, baselineEmissions);
        if (saved.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        return saved.divide(baselineEmissions, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
    }
}