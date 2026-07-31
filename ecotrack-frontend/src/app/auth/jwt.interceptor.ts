import { inject } from '@angular/core';
import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { Auth } from './auth'; // Adjust path to match your auth.ts location

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(Auth);
  const router = inject(Router);
  const token = authService.getToken();

  // If a token exists in memory, clone the request and set the authorization header
  let outgoingReq = req;
  if (token) {
    outgoingReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(outgoingReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Only force a logout+redirect for requests that were actually
      // carrying a session token. A 401 from an anonymous request (e.g. a
      // failed login attempt on /auth/login) is not "the session just
      // died mid-use" — that's just wrong credentials, and should surface
      // normally to whichever component made the call instead of bouncing
      // them off the login page they're already on.
      if (token && error.status === 401) {
        authService.logout();
        const backendMessage = error.error?.message;
        router.navigate(['/auth'], {
          queryParams: {
            sessionMessage: backendMessage || 'Your session has ended. Please sign in again.'
          }
        });
      }
      return throwError(() => error);
    })
  );
};