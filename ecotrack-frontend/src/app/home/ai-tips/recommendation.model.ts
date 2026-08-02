export interface TopRecommendationDto {
  category: string;
  suggestionText: string;
  impactEstimate: number;
}

export interface CategoryBreakdownDto {
  category: string;
  percentage: number;
}

export interface RecommendationResponse {
  topRecommendations: TopRecommendationDto[];
  allCategoriesSorted: CategoryBreakdownDto[];
}
