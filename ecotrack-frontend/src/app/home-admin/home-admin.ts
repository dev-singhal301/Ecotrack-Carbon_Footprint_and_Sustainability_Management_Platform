import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { Auth } from '../auth/auth';

/**
 * Admin Portal shell — mirrors Home's sidebar + <router-outlet> layout
 * so the two feel like one product, not two apps bolted together.
 *
 * Deliberately uses the real Auth service (not the legacy AuthService in
 * ../auth.service.ts that Home's logout still calls) so this new code
 * doesn't inherit that stale-token-key bug. Auth.logout() clears the
 * actual keys the app reads from (ecotrack_auth_token / _role / _has_password).
 */
@Component({
  selector: 'app-home-admin',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './home-admin.html',
  styleUrls: ['./home-admin.css'],
})
export class HomeAdmin {
  isSidebarOpen: boolean = false;

  constructor(
    private router: Router,
    private auth: Auth
  ) {}

  toggleSidebar(): void {
    this.isSidebarOpen = !this.isSidebarOpen;
  }

  closeSidebar(): void {
    this.isSidebarOpen = false;
  }

  // Same pattern as Home: give the freshly-activated child a chance to
  // load its own data (e.g. admin Dashboard's loadSummary(), Users' loadUsers()).
  onSubViewActivated(componentRef: any): void {
    if (componentRef && typeof componentRef.loadSummary === 'function') {
      componentRef.loadSummary();
    } else if (componentRef && typeof componentRef.loadUsers === 'function') {
      componentRef.loadUsers();
    } else if (componentRef && typeof componentRef.loadChallenges === 'function') {
      componentRef.loadChallenges();
    }
  }

  onLogout(event: Event): void {
    event.preventDefault();
    this.closeSidebar();
    this.auth.logout();
    this.router.navigate(['/auth']);
  }
}