import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { GoalInsightsBatchResponse } from './goal-insight.model';

export type GoalInsightStatus = 'idle' | 'loading' | 'ready' | 'error';

export interface GoalInsightState {
  status: GoalInsightStatus;
  data: GoalInsightsBatchResponse | null;
  error: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class GoalInsightService {
  private readonly baseUrl = 'http://localhost:8080/api/goal-insights';

  private readonly stateSubject = new BehaviorSubject<GoalInsightState>({
    status: 'idle',
    data: null,
    error: null
  });
  readonly state$ = this.stateSubject.asObservable();

  constructor(private http: HttpClient) {}

  get snapshot(): GoalInsightState {
    return this.stateSubject.value;
  }

  /** Fires the initial GET exactly once — no-op if already loading/ready. */
  ensureLoaded(): void {
    const current = this.stateSubject.value;
    if (current.status === 'loading' || current.status === 'ready') {
      return;
    }
    this.fetch(current.data);
  }

  /** No separate persisted table here (unlike Recommendations' delete+reinsert) —
   * goal progress is always live, so refresh just re-runs the same GET,
   * keeping old data visible while it's in flight. */
  refresh(): void {
    this.fetch(this.stateSubject.value.data);
  }

  private fetch(keepData: GoalInsightsBatchResponse | null): void {
    this.stateSubject.next({ status: 'loading', data: keepData, error: null });

    this.http.get<GoalInsightsBatchResponse>(this.baseUrl).subscribe({
      next: (res) => this.stateSubject.next({ status: 'ready', data: res, error: null }),
      error: (err) => this.stateSubject.next({
        status: 'error',
        data: keepData,
        error: err.error?.message || 'Could not load your goal insights. Please try again.'
      })
    });
  }
}
