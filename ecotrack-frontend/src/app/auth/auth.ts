import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';

interface RegisterData {
  name: string;
  email: string;
  password: string;
}

interface LoginData {
  email: string;
  password: string;
}

interface AuthResponse {
  token: string | null;
  name: string;
  email: string;
}

@Injectable({
  providedIn: 'root',
})
export class Auth {
  private baseUrl = 'http://localhost:8080/api/auth';
  private token: string | null = null; // in-memory storage, per team agreement

  constructor(private http: HttpClient) { }

  register(data: RegisterData): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/register`, data).pipe(
      catchError((error) => {
        const message = error.error?.message || 'Registration failed. Please try again.';
        return throwError(() => new Error(message));
      })
    );
  }

  login(data: LoginData): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, data).pipe(
      tap((response) => {
        this.token = response.token; // store in memory
      }),
      catchError((error) => {
        const message = error.error?.message || 'Invalid email or password.';
        return throwError(() => new Error(message));
      })
    );
  }

  getToken(): string | null {
    return this.token;
  }

  logout(): void {
    this.token = null;
  }
}