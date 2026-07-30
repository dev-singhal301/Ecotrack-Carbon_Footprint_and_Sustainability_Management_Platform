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
  ) { }

  toggleSidebar(): void {
    this.isSidebarOpen = !this.isSidebarOpen;
  }

  closeSidebar(): void {
    this.isSidebarOpen = false;
  }

  onSubViewActivated(componentRef: any): void {
    if (componentRef && typeof componentRef.loadSummary === 'function') {
      componentRef.loadSummary();
    }
  }

  onLogout(event: Event): void {
    event.preventDefault();
    this.closeSidebar();
    this.auth.logout();
    this.router.navigate(['/auth']);
  }
}