package com.ecotrack.ecotrack_backend.entity;

/**
 * Category options for Challenges specifically. Deliberately NOT the same
 * enum as CarbonCategory (used by CarbonEntry, Goals, EmissionFactor, and
 * Member 2's Recommendation module) — OVERALL only makes sense in a
 * challenge context ("reduce total emissions across every category" rather
 * than one specific one), and adding it to the shared CarbonCategory enum
 * would leak into code that has no idea what to do with it (e.g. an
 * EmissionFactor lookup keyed by category).
 *
 * The six CarbonCategory-matching values are intentionally spelled the same
 * as CarbonCategory's members so that a future carbon-reduction baseline
 * calculation (Challenge Module Design doc §6) can filter carbon_entries
 * by category name directly for anything except OVERALL, which instead
 * means "sum across every category" — that OVERALL-handling logic isn't
 * built yet and belongs in the join/baseline work, not here.
 */
public enum ChallengeCategory {
    OVERALL,
    TRANSPORTATION,
    ELECTRICITY,
    FOOD,
    WASTE,
    WATER,
    FUEL
}