package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;

public class TopRecommendationDto {

    private String category;
    private String suggestionText;
    private BigDecimal impactEstimate;

    public TopRecommendationDto() {}

    public TopRecommendationDto(String category, String suggestionText, BigDecimal impactEstimate) {
        this.category = category;
        this.suggestionText = suggestionText;
        this.impactEstimate = impactEstimate;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSuggestionText() { return suggestionText; }
    public void setSuggestionText(String suggestionText) { this.suggestionText = suggestionText; }

    public BigDecimal getImpactEstimate() { return impactEstimate; }
    public void setImpactEstimate(BigDecimal impactEstimate) { this.impactEstimate = impactEstimate; }
}