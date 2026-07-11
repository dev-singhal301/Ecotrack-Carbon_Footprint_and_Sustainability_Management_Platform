import { Routes } from '@angular/router';
import { AuthForm } from './auth/auth-form/auth-form';
import { Home } from './home/home';
import { ResetPassword } from './auth/reset-password/reset-password';

export const routes: Routes = [
    // Unify both under a single clean authentication route path
    { path: 'auth', component: AuthForm },
    { path: 'home', component: Home },
    
    // Redirect the empty base URL to your new unified auth page
    { path: '', redirectTo: 'auth', pathMatch: 'full' },

    { path: 'reset-password', component: ResetPassword },
    
    // Optional wildcard: Catch any stray typings or old bookmarks and send them to auth
    { path: '**', redirectTo: 'auth' }
];