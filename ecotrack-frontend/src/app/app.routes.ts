import { Routes } from '@angular/router';
import { AuthForm } from './auth/auth-form/auth-form';
import { Home } from './home/home';
import { ResetPassword } from './auth/reset-password/reset-password';
import { authGuard } from './auth/auth.guard';
import { adminGuard } from './auth/admin-guard';
import { HomeAdmin } from './home-admin/home-admin';
import { Dashboard as AdminDashboard } from './home-admin/dashboard/dashboard';
import { Users as AdminUsers } from './home-admin/users/users';
import { Challenges as AdminChallenges } from './home-admin/challenges/challenges';

// Import your newly generated dashboard sub-components
import { DashboardComponent } from './home/dashboard/dashboard';
import { TrackingComponent } from './home/tracking/tracking';
import { GoalsComponent } from './home/goals/goals';
import { ChallengesComponent } from './home/challenges/challenges';
import { AiTips } from './home/ai-tips/ai-tips';
import { Analytics } from './home/analytics/analytics';
import { Leaderboard } from './home/leaderboard/leaderboard';
import { Profile } from './home/profile/profile';
import { Settings } from './home/settings/settings';
import { SetPasswordPrompt } from './auth/set-password-prompt/set-password-prompt';
import { mustSetPasswordGuard } from './auth/must-set-password.guard';

export const routes: Routes = [
    // Unify both under a single clean authentication route path
    { path: 'auth', component: AuthForm },
    { path: 'reset-password', component: ResetPassword },
    { path: 'set-password', component: SetPasswordPrompt },
    
    // Parent Route for the Dashboard layout shell
    { 
        path: 'home', 
        component: Home,
        canActivate: [authGuard],
        canActivateChild: [mustSetPasswordGuard],
        children: [
            // Automatically drops users into the dashboard sub-view when reaching /home
            { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
            
            // Nested Child views that render inside home.html's <router-outlet>
            { path: 'dashboard', component: DashboardComponent },
            { path: 'tracking', component: TrackingComponent },
            { path: 'goals', component: GoalsComponent },
            { path: 'challenges', component: ChallengesComponent },
            { path: 'ai-tips', component: AiTips },
            { path: 'analytics', component: Analytics },
            { path: 'leaderboard', component: Leaderboard },
            { path: 'profile', component: Profile },
            { path: 'settings', component: Settings }
        ]
    },
    
    // Parent Route for the Admin Portal shell — same authGuard as /home
    // (must be logged in at all) plus adminGuard on top (must be ROLE_ADMIN).
    // mustSetPasswordGuard is intentionally NOT applied here: that flow is
    // for Google-only users setting a first password, and the seeded admin
    // account always has one.
    {
        path: 'home-admin',
        component: HomeAdmin,
        canActivate: [authGuard, adminGuard],
        children: [
            { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
            { path: 'dashboard', component: AdminDashboard },
            { path: 'users', component: AdminUsers },
            { path: 'challenges', component: AdminChallenges },
        ]
    },

    // Redirect the empty base URL to your unified auth page
    { path: '', redirectTo: 'auth', pathMatch: 'full' },
    
    // Wildcard: Catch any stray typings or old bookmarks and send them to auth
    { path: '**', redirectTo: 'auth' }
];