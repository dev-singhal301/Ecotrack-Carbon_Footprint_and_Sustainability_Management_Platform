import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { RecommendationResponse, RecStatus } from './recommendation.model';

export interface RecState {
  status: RecStatus;
  data: RecommendationResponse | null;
  error: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class RecommendationService {
  private readonly baseUrl = 'http://localhost:8080/api/recommendations';

  private readonly stateSubject = new BehaviorSubject<RecState>({
    status: 'idle',
    data: null,
    error: null
  });
  readonly state$ = this.stateSubject.asObservable();

  constructor(private http: HttpClient) {}

  get snapshot(): RecState {
    return this.stateSubject.value;
  }

  /**
   * Fires the initial GET exactly once. Safe to call from multiple places
   * (dashboard on mount, ai-tips as a safety net) — a no-op if a request is
   * already in flight or has already completed. Only refresh() ever refetches
   * after that.
   */
  ensureLoaded(): void {
    const current = this.stateSubject.value;
    if (current.status === 'loading' || current.status === 'ready') {
      return;
    }
    this.stateSubject.next({ status: 'loading', data: current.data, error: null });

    this.http.get<RecommendationResponse>(this.baseUrl).subscribe({
      next: (res) => this.stateSubject.next({ status: 'ready', data: res, error: null }),
      error: (err) => this.stateSubject.next({
        status: 'error',
        data: null,
        error: err.error?.message || 'Could not load your recommendations. Please try again.'
      })
    });
  }

  refresh(): void {
    const current = this.stateSubject.value;
    // Keep old data visible while refreshing, so the panel doesn't blank out.
    this.stateSubject.next({ status: 'loading', data: current.data, error: null });

    this.http.post<RecommendationResponse>(`${this.baseUrl}/refresh`, {}).subscribe({
      next: (res) => this.stateSubject.next({ status: 'ready', data: res, error: null }),
      error: (err) => this.stateSubject.next({
        status: 'error',
        data: current.data,
        error: err.error?.message || 'Could not refresh your recommendations. Please try again.'
      })
    });
  }
}
