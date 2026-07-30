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
  hasPassword: boolean;
  role: string; // "ROLE_ADMIN" or "ROLE_USER"
}

@Injectable({
  providedIn: 'root',
})
export class Auth {
  private baseUrl = 'http://localhost:8080/api/auth';
  private token: string | null = null; // in-memory storage, per team agreement
  private hasPasswordFlag: boolean | null = null;
  private roleValue: string | null = null;

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
        if (response.token) {
          // Sync with localStorage so it survives hard browser refreshes
          localStorage.setItem('ecotrack_auth_token', response.token);
        }
        this.hasPasswordFlag = response.hasPassword;
        localStorage.setItem('ecotrack_has_password', String(response.hasPassword));
        this.roleValue = response.role;
        localStorage.setItem('ecotrack_role', response.role);
      }),
      catchError((error) => {
        const message = error.error?.message || 'Invalid email or password.';
        return throwError(() => new Error(message));
      })
    );
  }

    googleLogin(idToken: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/google`, { idToken }).pipe(
      tap((response) => {
        this.token = response.token;
        if (response.token) {
          localStorage.setItem('ecotrack_auth_token', response.token);
        }
        this.hasPasswordFlag = response.hasPassword;
        localStorage.setItem('ecotrack_has_password', String(response.hasPassword));
        this.roleValue = response.role;
        localStorage.setItem('ecotrack_role', response.role);
      }),
      catchError((error) => {
        const message = error.error?.message || 'Google sign-in failed. Please try again.';
        return throwError(() => new Error(message));
      })
    );
  }

  forgotPassword(email: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/forgot-password`, { email }).pipe(
      catchError((error) => {
        const message = error.error?.message || (typeof error.error === 'string' ? error.error : 'Failed to process password reset request. Please try again.');
        return throwError(() => new Error(message));
      })
    );
  }

  resetPassword(token: string, newPassword: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/reset-password`, { token, newPassword }).pipe(
      catchError((error) => {
        const message = error.error?.message || (typeof error.error === 'string' ? error.error : 'Failed to reset password. The link may have expired.');
        return throwError(() => new Error(message));
      })
    );
  }

  setPassword(newPassword: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/set-password`, { newPassword }).pipe(
      tap(() => {
        this.hasPasswordFlag = true;
        localStorage.setItem('ecotrack_has_password', 'true');
      }),
      catchError((error) => {
        const message = error.error?.message || 'Failed to set password. Please try again.';
        return throwError(() => new Error(message));
      })
    );
  }

  hasPassword(): boolean {
    if (this.hasPasswordFlag === null) {
      this.hasPasswordFlag = localStorage.getItem('ecotrack_has_password') === 'true';
    }
    return this.hasPasswordFlag;
  }

  getToken(): string | null {
    // If the component memory is wiped by a refresh, look up the persistent key fallback
    if (!this.token) {
      this.token = localStorage.getItem('ecotrack_auth_token');
    }
    return this.token;
  }

  getRole(): string | null {
    if (!this.roleValue) {
      this.roleValue = localStorage.getItem('ecotrack_role');
    }
    return this.roleValue;
  }

  isAdmin(): boolean {
    return this.getRole() === 'ROLE_ADMIN';
  }

  logout(): void {
    this.token = null;
    this.hasPasswordFlag = null;
    this.roleValue = null;
    localStorage.removeItem('ecotrack_auth_token'); // Clean out completely on logout
    localStorage.removeItem('ecotrack_has_password');
    localStorage.removeItem('ecotrack_role');
  }
}