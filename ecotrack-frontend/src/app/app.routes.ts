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

// Home sub-components
import { DashboardComponent } from './home/dashboard/dashboard';
import { TrackingComponent } from './home/tracking/tracking';
import { GoalsComponent } from './home/goals/goals';
import { ChallengesComponent } from './home/challenges/challenges';
import { AiTips } from './home/ai-tips/ai-tips';
import { LeaderboardComponent } from './home/leaderboard/leaderboard';
<<<<<<< HEAD
import { Notifications } from './home/notifications/notifications';
import { Profile } from './home/profile/profile';
import { Settings } from './home/settings/settings';
=======
import { NotificationsComponent } from './home/notifications/notifications';
import { EcoIdentity } from './home/eco-identity/eco-identity';

>>>>>>> 9e5f3ef (Slight styling update for navbar and nav list modified)
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
            { path: 'leaderboard', component: LeaderboardComponent },
<<<<<<< HEAD
            { path: 'notifications', component: Notifications },
            { path: 'profile', component: Profile },
            { path: 'settings', component: Settings }
=======
            { path: 'notifications', component: NotificationsComponent },
            { path: 'eco-identity', component: EcoIdentity }
>>>>>>> 9e5f3ef (Slight styling update for navbar and nav list modified)
        ]
    },
    
    // Parent Route for the Admin Portal shell
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