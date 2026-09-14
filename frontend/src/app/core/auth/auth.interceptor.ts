import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService, isTokenRequest } from './auth.service';

/**
 * Attaches `Authorization: Bearer` to every request except POST /auth/token. A 401 from any other
 * endpoint clears the token and sends the user to /login, keeping the return URL. A 401 from the token
 * endpoint stays with the login form, which shows the message.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const tokenRequest = isTokenRequest(req.url);
  const token = auth.token();

  const outgoing =
    !tokenRequest && token !== null
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (!tokenRequest && error instanceof HttpErrorResponse && error.status === 401) {
        auth.logout('401 from the API');
        const returnUrl = router.url.startsWith('/login') ? undefined : router.url;
        void router.navigate(['/login'], { queryParams: { returnUrl } });
      }
      return throwError(() => error);
    }),
  );
};
