import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth';

// Runs *after* authGuard on the /home-admin route tree (authGuard already
// confirmed there's a valid token). This guard only narrows further: is the
// logged-in user specifically an ADMIN? A normal, authenticated user who
// wanders to /home-admin gets sent back to their own dashboard, not to
// /auth — they're not logged out, they're just not allowed in here.
export const adminGuard: CanActivateFn = (route, state) => {
  const authService = inject(Auth);
  const router = inject(Router);

  if (authService.isAdmin()) {
    return true;
  }

  router.navigate(['/home']);
  return false;
};