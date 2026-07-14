import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth'; // Adjust import path if necessary to point to your auth.ts

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(Auth);
  const router = inject(Router);

  // Read the token from the in-memory service property
  if (authService.getToken()) {
    return true; 
  }

  // No active token in memory, kick user back to auth screen
  router.navigate(['/auth']);
  return false;
};