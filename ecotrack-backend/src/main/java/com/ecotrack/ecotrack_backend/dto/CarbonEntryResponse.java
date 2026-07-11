package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CarbonEntryResponse {

    private Long entryId;
    private String category;
    private BigDecimal amount;
    private String unit;
    private BigDecimal carbonEmission;
    private LocalDate activityDate;

    public CarbonEntryResponse() {}

    public CarbonEntryResponse(Long entryId, String category, BigDecimal amount, String unit,
                                BigDecimal carbonEmission, LocalDate activityDate) {
        this.entryId = entryId;
        this.category = category;
        this.amount = amount;
        this.unit = unit;
        this.carbonEmission = carbonEmission;
        this.activityDate = activityDate;
    }

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getCarbonEmission() { return carbonEmission; }
    public void setCarbonEmission(BigDecimal carbonEmission) { this.carbonEmission = carbonEmission; }

    public LocalDate getActivityDate() { return activityDate; }
    public void setActivityDate(LocalDate activityDate) { this.activityDate = activityDate; }
}