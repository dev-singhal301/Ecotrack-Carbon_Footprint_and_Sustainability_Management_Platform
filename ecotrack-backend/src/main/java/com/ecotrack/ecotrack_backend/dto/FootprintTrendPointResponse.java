package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;

/**
 * A single day's snapshot for the emissions trend chart (line/area chart, optionally
 * stacked by category). One instance per day the user has a carbon_footprint row for.
 */
public class FootprintTrendPointResponse {
    private String period; // ISO yyyy-MM-dd
    private BigDecimal totalEmission;
    private BigDecimal electricityEmission;
    private BigDecimal transportationEmission;
    private BigDecimal fuelEmission;
    private BigDecimal waterEmission;
    private BigDecimal wasteEmission;
    private BigDecimal foodEmission;

    public FootprintTrendPointResponse() {}

    public FootprintTrendPointResponse(String period, BigDecimal totalEmission, BigDecimal electricityEmission,
                                        BigDecimal transportationEmission, BigDecimal fuelEmission,
                                        BigDecimal waterEmission, BigDecimal wasteEmission, BigDecimal foodEmission) {
        this.period = period;
        this.totalEmission = totalEmission;
        this.electricityEmission = electricityEmission;
        this.transportationEmission = transportationEmission;
        this.fuelEmission = fuelEmission;
        this.waterEmission = waterEmission;
        this.wasteEmission = wasteEmission;
        this.foodEmission = foodEmission;
    }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

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
}