import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { Auth } from '../auth/auth';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './home.html',
  styleUrls: ['./home.css']
})
export class Home {
  // Navigation sidebar drawer toggle state
  isSidebarOpen: boolean = false;

  constructor(
    private router: Router,
    private auth: Auth
  ) {}

  isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  toggleSidebar(): void {
    this.isSidebarOpen = !this.isSidebarOpen;
  }

  closeSidebar(): void {
    this.isSidebarOpen = false;
  }

  // Ensures child components (Dashboard, Goals, Tracking) fetch fresh data 
  // immediately upon direct page load or route activation
  onSubViewActivated(componentRef: any): void {
    if (componentRef) {
      if (typeof componentRef.loadSummary === 'function') {
        componentRef.loadSummary();
      } else if (typeof componentRef.loadGoals === 'function') {
        componentRef.loadGoals();
      } else if (typeof componentRef.loadLogs === 'function') {
        componentRef.loadLogs();
      }
    }
  }

  onLogout(event: Event): void {
    event.preventDefault();
    this.closeSidebar();
    // Was previously calling the legacy AuthService.logout(), which clears
    // 'ecotrack_jwt_token'/'ecotrack_user_email' — keys nothing else in the
    // app reads. That meant clicking Logout never actually cleared the real
    // session token ('ecotrack_auth_token'), just redirected the UI while
    // the token silently stayed valid in localStorage. Auth.logout() clears
    // the actual keys the app reads from.
    this.auth.logout();
    this.router.navigate(['/auth']);
  }
}