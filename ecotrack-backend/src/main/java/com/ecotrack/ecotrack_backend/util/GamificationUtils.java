package com.ecotrack.ecotrack_backend.util;

import com.ecotrack.ecotrack_backend.entity.User;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class GamificationUtils {

    // Threshold: 500 XP required per level progression
    private static final int XP_PER_LEVEL = 500;

    /**
     * Calculates the level of a user based on total XP.
     */
    public static int getLevel(User user) {
        int xp = (user != null && user.getCurrentXp() != null) ? user.getCurrentXp() : 0;
        return (xp / XP_PER_LEVEL) + 1;
    }

    /**
     * Calculates the target XP required for the user to reach the next level.
     */
    public static int getNextLevelXpTarget(User user) {
        int currentLevel = getLevel(user);
        return currentLevel * XP_PER_LEVEL;
    }

    /**
     * Returns the eco-rank title matching the user's level.
     */
    public static String getRankTitle(User user) {
        int level = getLevel(user);
        if (level >= 50) return "Mythic Eco";
        if (level >= 25) return "Eco Hero";
        if (level >= 20) return "Eco Master";
        if (level >= 15) return "Eco Champion";
        if (level >= 11) return "Eco Sentinel";
        if (level >= 8)  return "Eco Guardian";
        if (level >= 5)  return "Eco Pioneer";
        if (level >= 3)  return "Eco Explorer";
        if (level >= 2)  return "Eco Scout";
        return "Novice Eco";
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