package com.ecotrack.ecotrack_backend.dto;

import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import java.math.BigDecimal;
import java.util.Map;

public class FootprintOverviewResponse {
    private BigDecimal totalEmission;
    private Map<CarbonCategory, BigDecimal> categoryBreakdown;

    public FootprintOverviewResponse() {}
    public FootprintOverviewResponse(BigDecimal totalEmission, Map<CarbonCategory, BigDecimal> categoryBreakdown) {
        this.totalEmission = totalEmission;
        this.categoryBreakdown = categoryBreakdown;
    }
    public BigDecimal getTotalEmission() { return totalEmission; }
    public void setTotalEmission(BigDecimal totalEmission) { this.totalEmission = totalEmission; }
    public Map<CarbonCategory, BigDecimal> getCategoryBreakdown() { return categoryBreakdown; }
    public void setCategoryBreakdown(Map<CarbonCategory, BigDecimal> categoryBreakdown) { this.categoryBreakdown = categoryBreakdown; }
}