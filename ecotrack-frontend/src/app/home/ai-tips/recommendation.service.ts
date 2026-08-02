import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RecommendationResponse } from './recommendation.model';

@Injectable({
  providedIn: 'root'
})
export class RecommendationService {
  private readonly baseUrl = 'http://localhost:8080/api/recommendations';

  constructor(private http: HttpClient) {}

  // GET /api/recommendations
  // First-ever call (no prior refresh) auto-computes and persists server-side
  // (Decision B) — frontend never needs a separate "initialize" call.
  getRecommendations(): Observable<RecommendationResponse> {
    return this.http.get<RecommendationResponse>(this.baseUrl);
  }

  // POST /api/recommendations/refresh
  // Not called by the panel today — included for when a manual "refresh tips"
  // action is added later, so the endpoint contract is already in place.
  refreshRecommendations(): Observable<RecommendationResponse> {
    return this.http.post<RecommendationResponse>(`${this.baseUrl}/refresh`, {});
  }
}
