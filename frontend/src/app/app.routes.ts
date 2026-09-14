import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { LoginComponent } from './features/login/login.component';
import { SearchComponent } from './features/search/search.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, title: 'Log in · App Store search' },
  {
    path: 'search',
    component: SearchComponent,
    canActivate: [authGuard],
    title: 'App Store search',
  },
  { path: '', pathMatch: 'full', redirectTo: 'search' },
  { path: '**', redirectTo: 'search' },
];
