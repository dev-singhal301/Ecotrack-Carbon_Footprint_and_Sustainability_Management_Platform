package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;
import java.util.List;

public class TopRecommendationDto {

    private String category;
    private BigDecimal percentage;      // contribution % from the live breakdown
    private BigDecimal impactEstimate;  // kg CO2 for this category, this window
    private String title;
    private String why;
    private String action;
    private List<String> alternatives;
    private String impact;
    private String source; // "GEMINI" | "RULE_BASED"

    public TopRecommendationDto() {}

    public TopRecommendationDto(String category, BigDecimal percentage, BigDecimal impactEstimate,
                                AiRecommendationDetail detail) {
        this.category = category;
        this.percentage = percentage;
        this.impactEstimate = impactEstimate;
        this.title = detail.getTitle();
        this.why = detail.getWhy();
        this.action = detail.getAction();
        this.alternatives = detail.getAlternatives();
        this.impact = detail.getImpact();
        this.source = detail.getSource();
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }

    public BigDecimal getImpactEstimate() { return impactEstimate; }
    public void setImpactEstimate(BigDecimal impactEstimate) { this.impactEstimate = impactEstimate; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getWhy() { return why; }
    public void setWhy(String why) { this.why = why; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public List<String> getAlternatives() { return alternatives; }
    public void setAlternatives(List<String> alternatives) { this.alternatives = alternatives; }

    public String getImpact() { return impact; }
    public void setImpact(String impact) { this.impact = impact; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}