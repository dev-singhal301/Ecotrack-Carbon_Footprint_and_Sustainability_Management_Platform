package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.entity.CarbonCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Permanent rule-based fallback for generating recommendation suggestion text.
 * Not scaffolding — this stays even if an AI/LLM-based engine is added later,
 * to guarantee zero external API dependencies during demo testing.
 *
 * Each category has 4 tiers based on the 30-day rolling sum of `amount`
 * (raw unit per category: km, kWh, kg, litres — never carbonEmission).
 * Each tier returns at least 3 tips, joined into one string since
 * suggestion_text is a single VARCHAR(500) column, not a list.
 */
public class RecommendationRuleEngine {

    private static final String TIP_SEPARATOR = " | ";

    public String getSuggestion(CarbonCategory category, BigDecimal monthlySum) {
        double value = monthlySum.doubleValue();

        List<String> tips = switch (category) {
            case TRANSPORTATION -> transportationTips(value);
            case ELECTRICITY -> electricityTips(value);
            case FOOD -> foodTips(value);
            case WASTE -> wasteTips(value);
            case WATER -> waterTips(value);
            case FUEL -> fuelTips(value);
        };

        return String.join(TIP_SEPARATOR, tips);
    }

    private List<String> transportationTips(double km) {
        if (km <= 20) {
            return List.of(
                    "You're keeping transportation emissions low",
                    "Keep favoring walking or cycling for short trips",
                    "Continue using public transit when possible"
            );
        } else if (km <= 70) {
            return List.of(
                    "Consider public transport for regular trips",
                    "Try carpooling with a coworker or neighbor",
                    "Combine short errands into a single trip"
            );
        } else if (km <= 150) {
            return List.of(
                    "Replace some car trips with public transport",
                    "Look into carpooling options for your commute",
                    "Batch errands together to cut total distance driven"
            );
        } else {
            return List.of(
                    "Your transportation emissions are quite high",
                    "Prioritize public transport or carpooling where possible",
                    "Combine errands into fewer, longer trips",
                    "Consider a lower-emission commute option if available"
            );
        }
    }

    private List<String> electricityTips(double kWh) {
        if (kWh <= 100) {
            return List.of(
                    "Your electricity usage looks efficient",
                    "Keep unplugging devices when not in use",
                    "Continue favoring energy-efficient appliances"
            );
        } else if (kWh <= 250) {
            return List.of(
                    "Switch to LED lighting where you haven't already",
                    "Unplug idle appliances and chargers",
                    "Use natural light during the day when possible"
            );
        } else if (kWh <= 450) {
            return List.of(
                    "Switch remaining lighting to LEDs",
                    "Reduce standby power draw from electronics",
                    "Run high-draw appliances during off-peak hours"
            );
        } else {
            return List.of(
                    "Your electricity usage is high",
                    "Consider a home energy audit",
                    "Switch to LED lighting throughout",
                    "Reduce appliance standby power usage"
            );
        }
    }

    private List<String> foodTips(double kg) {
        if (kg <= 20) {
            return List.of(
                    "Your food-related emissions are low",
                    "Keep favoring sustainable food choices",
                    "Continue planning meals to avoid waste"
            );
        } else if (kg <= 40) {
            return List.of(
                    "Try adding more plant-based meals during the week",
                    "Buy only what you plan to cook to reduce waste",
                    "Consider seasonal, local produce where possible"
            );
        } else if (kg <= 70) {
            return List.of(
                    "Plan meals ahead to reduce over-purchasing",
                    "Shift a few meals a week to plant-based options",
                    "Store leftovers properly to cut food waste"
            );
        } else {
            return List.of(
                    "Your food-related emissions are high",
                    "Shift toward more plant-based meals",
                    "Plan meals to reduce over-purchasing",
                    "Reduce food waste through proper storage"
            );
        }
    }

    private List<String> wasteTips(double kg) {
        if (kg <= 10) {
            return List.of(
                    "Great job keeping waste output low",
                    "Keep sorting recyclables from general waste",
                    "Continue avoiding single-use items"
            );
        } else if (kg <= 20) {
            return List.of(
                    "Separate recyclables from general waste",
                    "Reduce single-use plastic where possible",
                    "Consider composting food scraps"
            );
        } else if (kg <= 40) {
            return List.of(
                    "Start separating recyclables consistently",
                    "Cut down on single-use packaging",
                    "Try composting to reduce landfill waste"
            );
        } else {
            return List.of(
                    "Your waste output is high",
                    "Prioritize recycling and composting",
                    "Reduce single-use items where possible",
                    "Look for ways to cut packaging waste"
            );
        }
    }

    private List<String> waterTips(double litres) {
        if (litres <= 2000) {
            return List.of(
                    "Your water usage is efficient",
                    "Keep up short showers and mindful use",
                    "Continue checking for leaks periodically"
            );
        } else if (litres <= 4000) {
            return List.of(
                    "Consider shortening shower times",
                    "Fix any dripping taps promptly",
                    "Turn off the tap while brushing teeth or washing dishes"
            );
        } else if (litres <= 7000) {
            return List.of(
                    "Fix leaks and shorten shower times",
                    "Consider installing low-flow fixtures",
                    "Reuse water where practical, like for plants"
            );
        } else {
            return List.of(
                    "Your water usage is high",
                    "Check for and fix any leaks",
                    "Install low-flow fixtures",
                    "Shorten shower times"
            );
        }
    }

    private List<String> fuelTips(double litres) {
        if (litres <= 15) {
            return List.of(
                    "Your fuel usage is low",
                    "Keep minimizing unnecessary driving",
                    "Continue combining errands when you do drive"
            );
        } else if (litres <= 35) {
            return List.of(
                    "Reduce unnecessary fuel-based trips",
                    "Combine errands to save fuel",
                    "Consider walking or cycling for short distances"
            );
        } else if (litres <= 60) {
            return List.of(
                    "Combine errands into fewer trips",
                    "Consider carpooling to share fuel costs",
                    "Maintain steady driving speeds to save fuel"
            );
        } else {
            return List.of(
                    "Your fuel usage is high",
                    "Try carpooling where possible",
                    "Combine errands into fewer trips",
                    "Use public transport for some journeys"
            );
        }
    }
}