package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import org.springframework.stereotype.Component;

import com.ecotrack.ecotrack_backend.dto.AiRecommendationDetail;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
/**
 * Permanent rule-based fallback for generating recommendation suggestion text.
 * Not scaffolding — stays even with Gemini wired in, to guarantee zero
 * external API dependencies during demo testing.
 *
 * Each tier now holds a larger pool of tips; getStructuredSuggestion() picks
 * 3 at random each call (1 action + 2 alternatives), so refresh genuinely
 * varies the fallback text instead of returning the same fixed set every time.
 */
@Component
public class RecommendationRuleEngine {

    public AiRecommendationDetail getStructuredSuggestion(CarbonCategory category, BigDecimal monthlySum,
                                                          BigDecimal percentage) {
        double value = monthlySum.doubleValue();

        List<String> pool = switch (category) {
            case TRANSPORTATION -> transportationTips(value);
            case ELECTRICITY -> electricityTips(value);
            case FOOD -> foodTips(value);
            case WASTE -> wasteTips(value);
            case WATER -> waterTips(value);
            case FUEL -> fuelTips(value);
        };

        List<String> picked = pickThree(pool);
        String action = picked.get(0);
        List<String> alternatives = picked.subList(1, picked.size());

        String title = category.name().charAt(0) + category.name().substring(1).toLowerCase()
                + " is a leading contributor";
        String why = "This category makes up " + percentage.toPlainString()
                + "% of your recorded emissions this period.";
        String impact = "Reducing " + category.name().toLowerCase()
                + " usage is likely to have a meaningful effect on your total footprint, since it's currently one of your top contributors.";

        return new AiRecommendationDetail(title, why, action, alternatives, impact, "STANDARD");
    }

    private static final String TIP_SEPARATOR = " | ";

    /**
     * Plain string-join version, still used by [whatever calls this with
     * (category, agg.sumAmount())] — kept in sync with getStructuredSuggestion()
     * by pulling from the same tier pools, just joined into one string instead
     * of split into action/alternatives.
     */
    public String getSuggestion(CarbonCategory category, BigDecimal monthlySum) {
        double value = monthlySum.doubleValue();

        List<String> pool = switch (category) {
            case TRANSPORTATION -> transportationTips(value);
            case ELECTRICITY -> electricityTips(value);
            case FOOD -> foodTips(value);
            case WASTE -> wasteTips(value);
            case WATER -> waterTips(value);
            case FUEL -> fuelTips(value);
        };

        return String.join(TIP_SEPARATOR, pickThree(pool));
    }

    /** Picks 3 distinct tips at random from the tier's pool, shuffled fresh each call. */
    private List<String> pickThree(List<String> pool) {
        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        return shuffled.subList(0, Math.min(3, shuffled.size()));
    }

    private List<String> transportationTips(double km) {
        if (km <= 20) {
            return List.of(
                    "Keep favoring walking or cycling for short trips",
                    "Continue using public transit when possible",
                    "Keep combining errands into a single trip",
                    "Stick with carpooling for the occasional longer trip",
                    "Maintain steady, fuel-efficient driving when you do drive",
                    "Keep choosing the lowest-emission option available for each trip"
            );
        } else if (km <= 70) {
            return List.of(
                    "Consider public transport for regular trips",
                    "Try carpooling with a coworker or neighbor",
                    "Combine short errands into a single trip",
                    "Try cycling for trips under 3km",
                    "Look into a weekly transit pass to make public transport the default",
                    "Group your weekly errands into one planned loop"
            );
        } else if (km <= 150) {
            return List.of(
                    "Replace some car trips with public transport",
                    "Look into carpooling options for your commute",
                    "Batch errands together to cut total distance driven",
                    "Try a hybrid commute — transit part-way, walk or cycle the rest",
                    "Check if remote/flexible days could cut a weekly commute trip",
                    "Plan routes in advance to avoid backtracking"
            );
        } else {
            return List.of(
                    "Prioritize public transport or carpooling where possible",
                    "Combine errands into fewer, longer trips",
                    "Consider a lower-emission commute option if available",
                    "Look into a carpool or vanpool for your regular commute",
                    "Audit your week for trips that could be merged or dropped",
                    "Check if a transit pass would work out cheaper and lower-emission"
            );
        }
    }

    private List<String> electricityTips(double kWh) {
        if (kWh <= 100) {
            return List.of(
                    "Keep unplugging devices when not in use",
                    "Continue favoring energy-efficient appliances",
                    "Keep using natural light during the day",
                    "Keep washing clothes on full loads only",
                    "Continue setting devices to sleep mode when idle",
                    "Keep monitoring your highest-draw appliances"
            );
        } else if (kWh <= 250) {
            return List.of(
                    "Switch to LED lighting where you haven't already",
                    "Unplug idle appliances and chargers",
                    "Use natural light during the day when possible",
                    "Set your thermostat a degree or two more efficiently",
                    "Wash clothes in cold water where possible",
                    "Turn off lights in unoccupied rooms"
            );
        } else if (kWh <= 450) {
            return List.of(
                    "Switch remaining lighting to LEDs",
                    "Reduce standby power draw from electronics",
                    "Run high-draw appliances during off-peak hours",
                    "Check your fridge/freezer seals for leaks",
                    "Use a power strip to fully cut standby devices at night",
                    "Air-dry laundry instead of using a dryer where possible"
            );
        } else {
            return List.of(
                    "Consider a home energy audit",
                    "Switch to LED lighting throughout",
                    "Reduce appliance standby power usage",
                    "Check insulation around doors and windows",
                    "Service or clean AC/heating units to keep them efficient",
                    "Shift heavy appliance use to off-peak hours"
            );
        }
    }

    private List<String> foodTips(double kg) {
        if (kg <= 20) {
            return List.of(
                    "Keep favoring sustainable food choices",
                    "Continue planning meals to avoid waste",
                    "Keep buying only what you plan to cook",
                    "Continue favoring seasonal, local produce",
                    "Keep portion sizes aligned to reduce leftovers going to waste",
                    "Keep a good mix of plant-based meals in the week"
            );
        } else if (kg <= 40) {
            return List.of(
                    "Try adding more plant-based meals during the week",
                    "Buy only what you plan to cook to reduce waste",
                    "Consider seasonal, local produce where possible",
                    "Plan a weekly menu to cut impulse purchases",
                    "Freeze extras instead of letting them spoil",
                    "Try swapping red meat for poultry or legumes a couple times a week"
            );
        } else if (kg <= 70) {
            return List.of(
                    "Plan meals ahead to reduce over-purchasing",
                    "Shift a few meals a week to plant-based options",
                    "Store leftovers properly to cut food waste",
                    "Check expiry dates before buying more of the same item",
                    "Try a 'use it up' night once a week to clear the fridge",
                    "Buy smaller, more frequent grocery batches instead of stocking up"
            );
        } else {
            return List.of(
                    "Shift toward more plant-based meals",
                    "Plan meals to reduce over-purchasing",
                    "Reduce food waste through proper storage",
                    "Track what gets thrown out for a week to spot patterns",
                    "Batch-cook to avoid last-minute high-impact takeout",
                    "Start composting unavoidable food scraps"
            );
        }
    }

    private List<String> wasteTips(double kg) {
        if (kg <= 10) {
            return List.of(
                    "Keep sorting recyclables from general waste",
                    "Continue avoiding single-use items",
                    "Keep composting where you can",
                    "Continue buying in bulk to cut packaging",
                    "Keep reusing containers where possible",
                    "Keep donating instead of discarding usable items"
            );
        } else if (kg <= 20) {
            return List.of(
                    "Separate recyclables from general waste",
                    "Reduce single-use plastic where possible",
                    "Consider composting food scraps",
                    "Switch to reusable bags and containers",
                    "Choose products with less packaging when you can",
                    "Repair rather than replace where it's practical"
            );
        } else if (kg <= 40) {
            return List.of(
                    "Start separating recyclables consistently",
                    "Cut down on single-use packaging",
                    "Try composting to reduce landfill waste",
                    "Batch-buy staples to cut packaging waste",
                    "Set up a dedicated recycling bin at home",
                    "Donate or resell items instead of discarding them"
            );
        } else {
            return List.of(
                    "Prioritize recycling and composting",
                    "Reduce single-use items where possible",
                    "Look for ways to cut packaging waste",
                    "Audit your bin for the single biggest recurring item",
                    "Switch to bulk or refillable options where available",
                    "Set a weekly waste-reduction goal to track progress"
            );
        }
    }

    private List<String> waterTips(double litres) {
        if (litres <= 2000) {
            return List.of(
                    "Keep up short showers and mindful use",
                    "Continue checking for leaks periodically",
                    "Keep the tap off while brushing or washing dishes",
                    "Continue running only full loads in the washer/dishwasher",
                    "Keep reusing greywater for plants where practical",
                    "Continue monitoring your water bill for sudden spikes"
            );
        } else if (litres <= 4000) {
            return List.of(
                    "Consider shortening shower times",
                    "Fix any dripping taps promptly",
                    "Turn off the tap while brushing teeth or washing dishes",
                    "Run the washer/dishwasher only on full loads",
                    "Check toilet flush mechanisms for silent leaks",
                    "Collect and reuse water from rinsing produce for plants"
            );
        } else if (litres <= 7000) {
            return List.of(
                    "Fix leaks and shorten shower times",
                    "Consider installing low-flow fixtures",
                    "Reuse water where practical, like for plants",
                    "Water the garden early morning or evening to cut evaporation loss",
                    "Switch to a shorter dishwasher/washer cycle where possible",
                    "Check outdoor taps and hoses for drips"
            );
        } else {
            return List.of(
                    "Check for and fix any leaks",
                    "Install low-flow fixtures",
                    "Shorten shower times",
                    "Consider a full plumbing check for hidden leaks",
                    "Switch to drought-tolerant plants if you water a garden",
                    "Track daily usage for a week to spot the biggest driver"
            );
        }
    }

    private List<String> fuelTips(double litres) {
        if (litres <= 15) {
            return List.of(
                    "Keep minimizing unnecessary driving",
                    "Continue combining errands when you do drive",
                    "Keep maintaining steady driving speeds",
                    "Continue keeping tires properly inflated for efficiency",
                    "Keep carpooling for the occasional longer trip",
                    "Continue choosing walking or cycling for very short trips"
            );
        } else if (litres <= 35) {
            return List.of(
                    "Reduce unnecessary fuel-based trips",
                    "Combine errands to save fuel",
                    "Consider walking or cycling for short distances",
                    "Keep tires properly inflated to improve efficiency",
                    "Avoid unnecessary idling to save fuel",
                    "Plan routes ahead to avoid backtracking"
            );
        } else if (litres <= 60) {
            return List.of(
                    "Combine errands into fewer trips",
                    "Consider carpooling to share fuel costs",
                    "Maintain steady driving speeds to save fuel",
                    "Get regular maintenance to keep fuel efficiency up",
                    "Cut down on short, cold-start trips where possible",
                    "Avoid heavy braking/acceleration to save fuel"
            );
        } else {
            return List.of(
                    "Try carpooling where possible",
                    "Combine errands into fewer trips",
                    "Use public transport for some journeys",
                    "Get a full vehicle checkup to rule out efficiency issues",
                    "Audit your week for trips that could be merged or dropped",
                    "Consider a lower-emission vehicle option for your next change"
            );
        }
    }
}