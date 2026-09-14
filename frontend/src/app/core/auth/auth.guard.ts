import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Without a token, redirects to /login and keeps the requested URL. */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).authenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
