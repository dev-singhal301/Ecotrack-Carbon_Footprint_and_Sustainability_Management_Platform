package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.GoalInsightDetail;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Permanent rule-based fallback for Goal Insights — Java port of the
 * frontend's goal-insight-messages.ts tip table, now the single source
 * of truth (frontend no longer generates insight text itself).
 * Stays in place even with Gemini wired in, same guarantee as
 * RecommendationRuleEngine.
 */
@Component
public class GoalInsightRuleEngine {

    public enum Status { ON_TRACK, AT_RISK, OVER_BUDGET, FAILED, COMPLETED }

    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "REDUCE_CARBON_EMISSIONS", "overall emissions",
            "REDUCE_TRANSPORTATION_EMISSIONS", "transportation",
            "REDUCE_ELECTRICITY_CONSUMPTION", "electricity",
            "REDUCE_FOOD_EMISSIONS", "food",
            "REDUCE_WASTE_GENERATION", "waste",
            "REDUCE_WATER_USAGE", "water",
            "REDUCE_FUEL_CONSUMPTION", "fuel"
    );

    // goalType -> status -> tip. Same 7x5 = 35 combinations as the TS version.
    private static final Map<String, Map<Status, String>> CATEGORY_TIPS = Map.ofEntries(
            Map.entry("REDUCE_CARBON_EMISSIONS", Map.of(
                    Status.ON_TRACK, "keeping up the mix of habits across categories that's been driving your total down",
                    Status.AT_RISK, "checking which single category has grown the most since you started and focusing there first",
                    Status.OVER_BUDGET, "cutting back sharply in whichever category is contributing the largest share right now",
                    Status.FAILED, "starting your next attempt with a narrower, single-category goal instead of one broad target",
                    Status.COMPLETED, "setting a slightly tighter overall target to build on this result"
            )),
            Map.entry("REDUCE_TRANSPORTATION_EMISSIONS", Map.of(
                    Status.ON_TRACK, "keeping favoring public transport or carpooling over solo drives",
                    Status.AT_RISK, "swapping one upcoming car trip for public transport or cycling",
                    Status.OVER_BUDGET, "switching to public transport or cycling for the rest of the period wherever possible",
                    Status.FAILED, "trying a smaller weekly distance target next time instead of one long-term number",
                    Status.COMPLETED, "setting a slightly lower distance target to keep the streak going"
            )),
            Map.entry("REDUCE_ELECTRICITY_CONSUMPTION", Map.of(
                    Status.ON_TRACK, "keeping unplugging idle devices and using off-peak hours where you can",
                    Status.AT_RISK, "switching a few more lights to LEDs this week to help close the gap",
                    Status.OVER_BUDGET, "cutting non-essential usage \u2014 AC, heaters, or standby devices \u2014 for the rest of the period",
                    Status.FAILED, "running a home energy audit before your next attempt to find where usage is highest",
                    Status.COMPLETED, "locking in the habit with a new, slightly more ambitious target"
            )),
            Map.entry("REDUCE_FOOD_EMISSIONS", Map.of(
                    Status.ON_TRACK, "keeping favoring plant-based meals and planning ahead to cut waste",
                    Status.AT_RISK, "shifting one more meal this week toward a plant-based option",
                    Status.OVER_BUDGET, "leaning heavily plant-based for the remaining days to limit further impact",
                    Status.FAILED, "trying a smaller, more specific target next time, like \"3 plant-based meals/week\"",
                    Status.COMPLETED, "keeping meals both sustainable and consistent going forward"
            )),
            Map.entry("REDUCE_WASTE_GENERATION", Map.of(
                    Status.ON_TRACK, "keeping sorting recyclables and composting where possible",
                    Status.AT_RISK, "separating recyclables more consistently for the rest of this week",
                    Status.OVER_BUDGET, "cutting back on single-use and packaging waste for the remaining days",
                    Status.FAILED, "starting with just packaging waste next time to make the goal more manageable",
                    Status.COMPLETED, "keeping the composting or recycling routine that got you here"
            )),
            Map.entry("REDUCE_WATER_USAGE", Map.of(
                    Status.ON_TRACK, "keeping showers short and taps off when not in use",
                    Status.AT_RISK, "checking for any leaks and shaving a minute off your showers",
                    Status.OVER_BUDGET, "cutting non-essential water use \u2014 shorter showers, full loads only \u2014 for the rest of the period",
                    Status.FAILED, "checking for leaks first next time, since that often gives the biggest single improvement",
                    Status.COMPLETED, "checking if a low-flow fixture upgrade could lock in the gains"
            )),
            Map.entry("REDUCE_FUEL_CONSUMPTION", Map.of(
                    Status.ON_TRACK, "keeping combining errands and carpooling where you can",
                    Status.AT_RISK, "combining a couple of errands into a single trip this week",
                    Status.OVER_BUDGET, "minimizing non-essential trips for the rest of the period",
                    Status.FAILED, "carpooling for even a few trips next time, since that can meaningfully move the number",
                    Status.COMPLETED, "building on this streak with a new target"
            ))
    );

    private static final Map<Status, String> DEFAULT_TIPS = Map.of(
            Status.ON_TRACK, "keeping up your current habits",
            Status.AT_RISK, "making a small adjustment soon to close the gap",
            Status.OVER_BUDGET, "cutting back sharply for the remaining days",
            Status.FAILED, "setting a more specific target next time",
            Status.COMPLETED, "building on this result with your next goal"
    );

    public Status getStatus(LocalDate startDate, LocalDate endDate,
                            BigDecimal completionPercentage, BigDecimal currentValue,
                            BigDecimal targetValue, boolean completed) {
        if (completed) return Status.COMPLETED;

        LocalDate now = LocalDate.now();
        if (now.isAfter(endDate)) {
            return currentValue.compareTo(targetValue) <= 0 ? Status.COMPLETED : Status.FAILED;
        }
        if (completionPercentage.compareTo(BigDecimal.valueOf(100)) >= 0) return Status.OVER_BUDGET;

        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
        long daysElapsed = ChronoUnit.DAYS.between(startDate, now);
        double expectedProgress = totalDays > 0 ? (daysElapsed / (double) totalDays) * 100 : 0;

        return completionPercentage.doubleValue() <= expectedProgress ? Status.ON_TRACK : Status.AT_RISK;
    }

    public GoalInsightDetail getStructuredInsight(String goalType, Status status,
                                                  String currentDisplay, String targetDisplay,
                                                  String endDateDisplay, long daysRemaining) {
        String label = CATEGORY_LABELS.getOrDefault(goalType, "this");
        String tip = CATEGORY_TIPS.getOrDefault(goalType, Map.of()).getOrDefault(status, DEFAULT_TIPS.get(status));
        String dayWord = daysRemaining == 1 ? "day" : "days";

        // Lean, factual context only — no status announcement (badge already
        // shows that) and no tip repeat (Next Best Action already shows that).
        String message = switch (status) {
            case ON_TRACK -> "%s of your %s limit used so far, with %d %s left until %s \u2014 right about where you'd expect to be at this point."
                    .formatted(currentDisplay, targetDisplay, daysRemaining, dayWord, endDateDisplay);
            case AT_RISK -> "%s of your %s limit used already, more than expected with %d %s remaining until %s. At this rate you're likely to go over before the deadline."
                    .formatted(currentDisplay, targetDisplay, daysRemaining, dayWord, endDateDisplay);
            case OVER_BUDGET -> "%s used against a %s limit, with %d %s still left until %s. The goal is unlikely to complete successfully unless usage drops sharply for the rest of the period."
                    .formatted(currentDisplay, targetDisplay, daysRemaining, dayWord, endDateDisplay);
            case FAILED -> "This %s goal ended on %s at %s against a %s limit."
                    .formatted(label, endDateDisplay, currentDisplay, targetDisplay);
            case COMPLETED -> "%s usage kept to %s, staying under the %s limit by the %s deadline."
                    .formatted(label, currentDisplay, targetDisplay, endDateDisplay);
        };

        String action = switch (status) {
            case ON_TRACK -> "Keep " + tip + ".";
            case AT_RISK, OVER_BUDGET -> "Start " + tip + " today.";
            case FAILED -> "Next time, try " + tip + ".";
            case COMPLETED -> Character.toUpperCase(tip.charAt(0)) + tip.substring(1) + ".";
        };

        return new GoalInsightDetail(status.name(), message, action, "STANDARD");
    }
}