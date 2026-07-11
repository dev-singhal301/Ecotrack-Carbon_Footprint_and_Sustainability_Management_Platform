package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;

public class DashboardSummaryResponse {

    private BigDecimal totalEmission;
    private BigDecimal electricityEmission;
    private BigDecimal transportationEmission;
    private BigDecimal fuelEmission;
    private BigDecimal waterEmission;
    private BigDecimal wasteEmission;
    private BigDecimal foodEmission;
    private long activeGoalsCount;
    private long completedGoalsCount;

    public DashboardSummaryResponse() {}

    // getters and setters
    public BigDecimal getTotalEmission() { return totalEmission; }
    public void setTotalEmission(BigDecimal totalEmission) { this.totalEmission = totalEmission; }

    public BigDecimal getElectricityEmission() { return electricityEmission; }
    public void setElectricityEmission(BigDecimal electricityEmission) { this.electricityEmission = electricityEmission; }

    public BigDecimal getTransportationEmission() { return transportationEmission; }
    public void setTransportationEmission(BigDecimal transportationEmission) { this.transportationEmission = transportationEmission; }

    public BigDecimal getFuelEmission() { return fuelEmission; }
    public void setFuelEmission(BigDecimal fuelEmission) { this.fuelEmission = fuelEmission; }

    public BigDecimal getWaterEmission() { return waterEmission; }
    public void setWaterEmission(BigDecimal waterEmission) { this.waterEmission = waterEmission; }

    public BigDecimal getWasteEmission() { return wasteEmission; }
    public void setWasteEmission(BigDecimal wasteEmission) { this.wasteEmission = wasteEmission; }

    public BigDecimal getFoodEmission() { return foodEmission; }
    public void setFoodEmission(BigDecimal foodEmission) { this.foodEmission = foodEmission; }

    public long getActiveGoalsCount() { return activeGoalsCount; }
    public void setActiveGoalsCount(long activeGoalsCount) { this.activeGoalsCount = activeGoalsCount; }

    public long getCompletedGoalsCount() { return completedGoalsCount; }
    public void setCompletedGoalsCount(long completedGoalsCount) { this.completedGoalsCount = completedGoalsCount; }
}