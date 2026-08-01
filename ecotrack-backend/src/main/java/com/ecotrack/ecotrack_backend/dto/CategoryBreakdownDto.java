package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;

public class CategoryBreakdownDto {

    private String category;
    private BigDecimal percentage;

    public CategoryBreakdownDto() {}

    public CategoryBreakdownDto(String category, BigDecimal percentage) {
        this.category = category;
        this.percentage = percentage;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
}