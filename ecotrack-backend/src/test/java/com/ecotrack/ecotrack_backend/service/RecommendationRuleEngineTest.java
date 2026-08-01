package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationRuleEngineTest {

    private final RecommendationRuleEngine ruleEngine = new RecommendationRuleEngine();

    // --- Every category always returns at least 3 tips, never null/empty ---

    @Test
    void getSuggestion_everyCategory_returnsAtLeastThreeTips() {
        for (CarbonCategory category : CarbonCategory.values()) {
            String suggestion = ruleEngine.getSuggestion(category, new BigDecimal("10"));
            assertNotNull(suggestion, "Suggestion should never be null for " + category);
            String[] tips = suggestion.split(" \\| ");
            assertTrue(tips.length >= 3,
                    "Expected at least 3 tips for " + category + " but got " + tips.length);
        }
    }

    @Test
    void getSuggestion_everyCategory_highestTier_returnsAtLeastThreeTips() {
        BigDecimal highValue = new BigDecimal("100000"); // comfortably above every category's top tier
        for (CarbonCategory category : CarbonCategory.values()) {
            String suggestion = ruleEngine.getSuggestion(category, highValue);
            String[] tips = suggestion.split(" \\| ");
            assertTrue(tips.length >= 3,
                    "Expected at least 3 tips at highest tier for " + category);
        }
    }

    // --- Tier boundaries produce different content (not the same tip regardless of amount) ---

    @Test
    void transportation_lowVsHighTier_returnDifferentSuggestions() {
        String low = ruleEngine.getSuggestion(CarbonCategory.TRANSPORTATION, new BigDecimal("5"));
        String high = ruleEngine.getSuggestion(CarbonCategory.TRANSPORTATION, new BigDecimal("200"));
        assertNotEquals(low, high);
    }

    @Test
    void electricity_lowVsHighTier_returnDifferentSuggestions() {
        String low = ruleEngine.getSuggestion(CarbonCategory.ELECTRICITY, new BigDecimal("50"));
        String high = ruleEngine.getSuggestion(CarbonCategory.ELECTRICITY, new BigDecimal("500"));
        assertNotEquals(low, high);
    }

    @Test
    void food_lowVsHighTier_returnDifferentSuggestions() {
        String low = ruleEngine.getSuggestion(CarbonCategory.FOOD, new BigDecimal("10"));
        String high = ruleEngine.getSuggestion(CarbonCategory.FOOD, new BigDecimal("80"));
        assertNotEquals(low, high);
    }

    @Test
    void waste_lowVsHighTier_returnDifferentSuggestions() {
        String low = ruleEngine.getSuggestion(CarbonCategory.WASTE, new BigDecimal("5"));
        String high = ruleEngine.getSuggestion(CarbonCategory.WASTE, new BigDecimal("50"));
        assertNotEquals(low, high);
    }

    @Test
    void water_lowVsHighTier_returnDifferentSuggestions() {
        String low = ruleEngine.getSuggestion(CarbonCategory.WATER, new BigDecimal("1000"));
        String high = ruleEngine.getSuggestion(CarbonCategory.WATER, new BigDecimal("8000"));
        assertNotEquals(low, high);
    }

    @Test
    void fuel_lowVsHighTier_returnDifferentSuggestions() {
        String low = ruleEngine.getSuggestion(CarbonCategory.FUEL, new BigDecimal("5"));
        String high = ruleEngine.getSuggestion(CarbonCategory.FUEL, new BigDecimal("70"));
        assertNotEquals(low, high);
    }

    // --- Zero amount is a valid input, never throws ---

    @Test
    void getSuggestion_zeroAmount_everyCategory_doesNotThrow() {
        for (CarbonCategory category : CarbonCategory.values()) {
            assertDoesNotThrow(() -> ruleEngine.getSuggestion(category, BigDecimal.ZERO));
        }
    }

    // --- Boundary edge: value exactly at a tier threshold is handled (no exception, still 3+ tips) ---

    @Test
    void getSuggestion_exactBoundaryValue_transportation_stillReturnsValidSuggestion() {
        // 20 is the TRANSPORTATION low/moderate boundary (<=20 vs >20)
        String atBoundary = ruleEngine.getSuggestion(CarbonCategory.TRANSPORTATION, new BigDecimal("20"));
        assertTrue(atBoundary.split(" \\| ").length >= 3);
    }
}