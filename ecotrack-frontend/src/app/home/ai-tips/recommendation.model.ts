export interface TopRecommendationDto {
  category: string;
  percentage: number;
  impactEstimate: number;
  title: string;
  why: string;
  action: string;
  alternatives: string[];
  impact: string;
  source: 'AI' | 'STANDARD';
}

export interface CategoryBreakdownDto {
  category: string;
  percentage: number;
}

export interface RecommendationResponse {
  topRecommendations: TopRecommendationDto[];
  allCategoriesSorted: CategoryBreakdownDto[];
  message: string | null;
}

export type RecStatus = 'idle' | 'loading' | 'ready' | 'error';
