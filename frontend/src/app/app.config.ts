import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { debugLogInterceptor } from './core/debug/debug-log.interceptor';
import { provideAppIcons } from './core/icons/app-icons';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // The debug log is the outer interceptor, so it also sees the 401s the auth interceptor handles
    provideHttpClient(withInterceptors([debugLogInterceptor, authInterceptor])),
    provideAppIcons(),
  ],
};
