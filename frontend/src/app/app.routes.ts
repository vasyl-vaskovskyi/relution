import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, title: 'Log in · App Store search' },
  { path: '**', redirectTo: 'login' },
];
