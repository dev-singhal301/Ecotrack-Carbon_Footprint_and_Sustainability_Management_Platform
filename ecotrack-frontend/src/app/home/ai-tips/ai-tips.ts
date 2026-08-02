import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { RecommendationService } from './recommendation.service';
import { RecommendationResponse } from './recommendation.model';

type EcoBotPanel = 'recommendations' | 'goals' | 'facts' | null;

@Component({
  selector: 'app-ai-tips',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ai-tips.html',
  styleUrl: './ai-tips.css',
})
export class AiTips {
  activePanel: EcoBotPanel = null;

  recommendations: RecommendationResponse | null = null;
  recLoading = false;
  recError: string | null = null;
  revealedCount = 1;

  private readonly categoryLabels: Record<string, string> = {
    TRANSPORTATION: 'Transportation',
    ELECTRICITY: 'Electricity',
    FOOD: 'Food',
    WASTE: 'Waste',
    WATER: 'Water',
    FUEL: 'Fuel',
  };

  constructor(
    private router: Router,
    private recommendationService: RecommendationService
  ) {}

  selectPanel(panel: EcoBotPanel): void {
    this.activePanel = this.activePanel === panel ? null : panel;

    if (this.activePanel === 'recommendations' && !this.recommendations && !this.recLoading) {
      this.loadRecommendations();
    }
  }

  loadRecommendations(): void {
    this.recLoading = true;
    this.recError = null;

    this.recommendationService.getRecommendations().subscribe({
      next: (res) => {
        this.recommendations = res;
        this.revealedCount = 1;
        this.recLoading = false;
      },
      error: (err) => {
        console.error('Failed to load recommendations:', err);
        this.recError = err.error?.message || 'Could not load your recommendations. Please try again.';
        this.recLoading = false;
      },
    });
  }

  // Locally revealed slice of the full 6-category breakdown — no new API call.
  get revealedCategories() {
    if (!this.recommendations) return [];
    return this.recommendations.allCategoriesSorted.slice(0, this.revealedCount);
  }

  get hasMoreToExplore(): boolean {
    if (!this.recommendations) return false;
    return this.revealedCount < this.recommendations.allCategoriesSorted.length;
  }

  get nextCategoryLabel(): string {
    if (!this.recommendations) return '';
    const next = this.recommendations.allCategoriesSorted[this.revealedCount];
    return next ? this.getCategoryLabel(next.category) : '';
  }

  exploreNext(): void {
    if (this.hasMoreToExplore) {
      this.revealedCount++;
    }
  }

  // Only categories in the top 3 have an actual tip; the rest just show percentage.
  getTipFor(category: string) {
    return this.recommendations?.topRecommendations.find((r) => r.category === category) ?? null;
  }

  getCategoryLabel(category: string): string {
    return this.categoryLabels[category] ?? category;
  }

  goToDashboard(): void {
    this.router.navigate(['/home/dashboard']);
  }
}
