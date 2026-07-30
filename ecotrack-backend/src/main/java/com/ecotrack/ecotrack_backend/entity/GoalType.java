package com.ecotrack.ecotrack_backend.entity;

public enum GoalType {
    REDUCE_CARBON_EMISSIONS,
    REDUCE_ELECTRICITY_CONSUMPTION,
    REDUCE_WATER_USAGE,
    REDUCE_TRANSPORTATION_EMISSIONS,
    REDUCE_FUEL_CONSUMPTION,
    REDUCE_FOOD_EMISSIONS,
    REDUCE_WASTE_GENERATION;

    /**
     * Utility method to safely parse String input into a valid GoalType enum.
     */
    public static GoalType fromDbString(String text) {
        if (text == null) return null;
        for (GoalType type : GoalType.values()) {
            if (type.name().equalsIgnoreCase(text.trim())) {
                return type;
            }
        }
        return null;
    }
}