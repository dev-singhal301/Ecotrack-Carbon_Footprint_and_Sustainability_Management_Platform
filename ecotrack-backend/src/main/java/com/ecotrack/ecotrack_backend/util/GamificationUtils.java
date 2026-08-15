package com.ecotrack.ecotrack_backend.util;

import com.ecotrack.ecotrack_backend.dto.UserBadgeResponse;
import com.ecotrack.ecotrack_backend.entity.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class GamificationUtils {

    // Fallback only — used if a User somehow has no persisted userLevel yet.
    // NOTE: this must stay in sync with whatever awards XP/levels elsewhere in the app.
    // Prefer user.getUserLevel() (the persisted, authoritative value shown on the
    // profile XP bar) over recomputing from XP, so badge eligibility never drifts
    // out of sync with the level the user actually sees on screen.
    private static final int XP_PER_LEVEL_FALLBACK = 50;

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
     * Returns the user's level. Always prefers the persisted user_level column —
     * the same value shown on the profile XP bar — so badge eligibility can never
     * disagree with what the user sees elsewhere in the UI.
     */
    public static int getLevel(User user) {
        if (user != null && user.getUserLevel() != null) {
            return user.getUserLevel();
        }
        int xp = (user != null && user.getCurrentXp() != null) ? user.getCurrentXp() : 0;
        return (xp / XP_PER_LEVEL_FALLBACK) + 1;
    }

    /**
     * Calculates the target XP required for the user to reach the next level.
     * Only used as a fallback — the profile screen uses the persisted
     * next_level_xp_target column directly.
     */
    public static int getNextLevelXpTarget(User user) {
        int currentLevel = getLevel(user);
        return currentLevel * XP_PER_LEVEL_FALLBACK;
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
     * Every title the user has passed through stays in their badge collection
     * (e.g. reaching "Eco Pioneer" keeps "Novice Eco", "Eco Scout", "Eco Explorer" too).
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
                badge.setRequiredXp((m.getRequiredLevel() - 1) * XP_PER_LEVEL_FALLBACK);
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