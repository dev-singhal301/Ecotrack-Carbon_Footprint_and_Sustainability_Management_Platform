package com.ecotrack.ecotrack_backend.dto;

import java.util.List;

/**
 * The structured AI (or rule-based fallback) content for one category.
 * This is what gets JSON-serialized into Recommendation.suggestionText,
 * and what a Gemini batch response is parsed into.
 */
public class AiRecommendationDetail {

    private String title;
    private String why;
    private String action;
    private List<String> alternatives;
    private String impact;
    private String source; // "GEMINI" | "RULE_BASED"

    public AiRecommendationDetail() {}

    public AiRecommendationDetail(String title, String why, String action,
                                  List<String> alternatives, String impact, String source) {
        this.title = title;
        this.why = why;
        this.action = action;
        this.alternatives = alternatives;
        this.impact = impact;
        this.source = source;
    }

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