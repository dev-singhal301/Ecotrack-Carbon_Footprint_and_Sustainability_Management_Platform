import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CarbonEntryRequest {
  category: string;
  amount: number | null;
  unit: string;
  activityDate: string;
}

export interface CarbonEntryResponse {
  entryId: number;
  category: string;
  amount: number;
  unit: string;
  carbonEmission: number;
  activityDate: string;
}

@Injectable({
  providedIn: 'root'
})
export class CarbonEntryService {
  private apiUrl = 'http://localhost:8080/api/carbon-entries'; // Spring Boot RestController path [cite: 16]

  constructor(private http: HttpClient) {}

  // Maps directly to @PostMapping CarbonEntryController [cite: 22]
  createEntry(entry: CarbonEntryRequest): Observable<CarbonEntryResponse> {
    return this.http.post<CarbonEntryResponse>(this.apiUrl, entry);
  }

  // Maps directly to @GetMapping CarbonEntryController with request params [cite: 23]
  getHistory(category?: string, from?: string, to?: string): Observable<CarbonEntryResponse[]> {
    let params = new HttpParams();
    if (category) params = params.set('category', category);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);

    return this.http.get<CarbonEntryResponse[]>(this.apiUrl, { params });
  }

  // Maps directly to @DeleteMapping("/{id}") CarbonEntryController [cite: 25]
  deleteEntry(entryId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${entryId}`);
  }
}