package com.ecotrack.ecotrack_backend.dto;

import java.util.List;

public class RecommendationResponse {

    private List<TopRecommendationDto> topRecommendations;
    private List<CategoryBreakdownDto> allCategoriesSorted;
    private String message;

    public RecommendationResponse() {}

    public RecommendationResponse(List<TopRecommendationDto> topRecommendations,
                                  List<CategoryBreakdownDto> allCategoriesSorted,
                                  String message) {
        this.topRecommendations = topRecommendations;
        this.allCategoriesSorted = allCategoriesSorted;
        this.message = message;
    }

    public List<TopRecommendationDto> getTopRecommendations() { return topRecommendations; }
    public void setTopRecommendations(List<TopRecommendationDto> topRecommendations) { this.topRecommendations = topRecommendations; }

    public List<CategoryBreakdownDto> getAllCategoriesSorted() { return allCategoriesSorted; }
    public void setAllCategoriesSorted(List<CategoryBreakdownDto> allCategoriesSorted) { this.allCategoriesSorted = allCategoriesSorted; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}