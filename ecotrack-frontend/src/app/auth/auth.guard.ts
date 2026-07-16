import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth'; // Points perfectly to your auth.ts service file

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(Auth);
  const router = inject(Router);

  // Checks the in-memory property, which falls back to localStorage automatically if refreshed
  if (authService.getToken()) {
    return true; 
  }

  // No token found in memory or persistent storage, kick user back to login screen
  router.navigate(['/auth']);
  return false;
};