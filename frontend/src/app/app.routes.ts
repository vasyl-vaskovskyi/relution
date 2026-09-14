import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

// Every view is a lazy chunk, so the initial bundle holds only the shell and stays within its budget
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
    title: 'Log in · App Store search',
  },
  {
    path: 'search',
    loadComponent: () =>
      import('./features/search/search.component').then((m) => m.SearchComponent),
    canActivate: [authGuard],
    title: 'App Store search',
  },
  {
    path: 'apps/:id',
    loadComponent: () =>
      import('./features/app-details/app-details.component').then((m) => m.AppDetailsComponent),
    canActivate: [authGuard],
    title: 'App details · App Store search',
  },
  { path: '', pathMatch: 'full', redirectTo: 'search' },
  { path: '**', redirectTo: 'search' },
];
