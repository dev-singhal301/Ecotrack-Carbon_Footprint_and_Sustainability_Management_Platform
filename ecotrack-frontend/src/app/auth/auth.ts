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

  /**
   * Sends a password reset request to the backend.
   * Handles both JSON error objects and plain text backend responses.
   */
  forgotPassword(email: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/forgot-password`, { email }).pipe(
      catchError((error) => {
        // Spring Boot sometimes returns plain text or a JSON object depending on your controller setup
        const message = error.error?.message || (typeof error.error === 'string' ? error.error : 'Failed to process password reset request. Please try again.');
        return throwError(() => new Error(message));
      })
    );
  }

  /**
   * Sends the recovery token and new password to the backend to complete the reset.
   */
  resetPassword(token: string, newPassword: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/reset-password`, { token, newPassword }).pipe(
      catchError((error) => {
        const message = error.error?.message || (typeof error.error === 'string' ? error.error : 'Failed to reset password. The link may have expired.');
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