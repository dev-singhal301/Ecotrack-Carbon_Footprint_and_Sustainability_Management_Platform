import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { FactResponse } from './fact.model';

@Injectable({
  providedIn: 'root'
})
export class FactService {
  private readonly baseUrl = 'http://localhost:8080/api/facts';

  constructor(private http: HttpClient) {}

  // GET /api/facts/random — non-personalized, safe to call repeatedly.
  getRandomFact(): Observable<FactResponse> {
    return this.http.get<FactResponse>(`${this.baseUrl}/random`);
  }
}
