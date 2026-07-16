import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly TOKEN_KEY = 'ecotrack_jwt_token';
  private readonly USER_EMAIL = 'ecotrack_user_email';

  // Call this method inside your Login page component when handling the successful backend response
  saveSession(token: string, email: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
    localStorage.setItem(this.USER_EMAIL, email);
  }

  // Survives browser refresh events!
  isAuthenticated(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getUserEmail(): string | null {
    return localStorage.getItem(this.USER_EMAIL);
  }

  // Clears the token string when logging out
  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_EMAIL);
  }
}