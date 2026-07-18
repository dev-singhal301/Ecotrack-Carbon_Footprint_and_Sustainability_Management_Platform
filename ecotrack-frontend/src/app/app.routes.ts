import { Routes } from '@angular/router';
import { AuthForm } from './auth/auth-form/auth-form';
import { Home } from './home/home';
import { ResetPassword } from './auth/reset-password/reset-password';
import { authGuard } from './auth/auth.guard'; // 👈 1. Import your brand new guard

// Import your newly generated dashboard sub-components
import { Dashboard } from './home/dashboard/dashboard';
import { TrackingComponent } from './home/tracking/tracking';
import { GoalsComponent } from './home/goals/goals';
import { Challenges } from './home/challenges/challenges';
import { AiTips } from './home/ai-tips/ai-tips';
import { Analytics } from './home/analytics/analytics';
import { Leaderboard } from './home/leaderboard/leaderboard';
import { Profile } from './home/profile/profile';
import { Settings } from './home/settings/settings';

export const routes: Routes = [
    // Unify both under a single clean authentication route path
    { path: 'auth', component: AuthForm },
    { path: 'reset-password', component: ResetPassword },
    
    // Parent Route for the Dashboard layout shell
    { 
        path: 'home', 
        component: Home,
        canActivate: [authGuard], // 👈 2. Add the guard here to protect this shell and all its children
        children: [
            // Automatically drops users into the dashboard sub-view when reaching /home
            { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
            
            // Nested Child views that render inside home.html's <router-outlet>
            { path: 'dashboard', component: Dashboard },
            { path: 'tracking', component: TrackingComponent },
            { path: 'goals', component: GoalsComponent },
            { path: 'challenges', component: Challenges },
            { path: 'ai-tips', component: AiTips },
            { path: 'analytics', component: Analytics },
            { path: 'leaderboard', component: Leaderboard },
            { path: 'profile', component: Profile },
            { path: 'settings', component: Settings }
        ]
    },
    
    // Redirect the empty base URL to your unified auth page
    { path: '', redirectTo: 'auth', pathMatch: 'full' },
    
    // Wildcard: Catch any stray typings or old bookmarks and send them to auth
    { path: '**', redirectTo: 'auth' }
];