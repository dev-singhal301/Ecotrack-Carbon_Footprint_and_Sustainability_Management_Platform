package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;

public class CategoryImpactResponse {

    private String categoryName;
    private BigDecimal totalEmissions; // Emissions in kg CO2e
    private Double percentageShare;    // Proportional percentage (0.0 to 100.0%) for progress bars

    // Default constructor
    public CategoryImpactResponse() {
    }

    // All-args constructor
    public CategoryImpactResponse(String categoryName, BigDecimal totalEmissions, Double percentageShare) {
        this.categoryName = categoryName;
        this.totalEmissions = totalEmissions;
        this.percentageShare = percentageShare;
    }

    // Getters and Setters
    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public BigDecimal getTotalEmissions() {
        return totalEmissions;
    }

    public void setTotalEmissions(BigDecimal totalEmissions) {
        this.totalEmissions = totalEmissions;
    }

    public Double getPercentageShare() {
        return percentageShare;
    }

    public void setPercentageShare(Double percentageShare) {
        this.percentageShare = percentageShare;
    }
}