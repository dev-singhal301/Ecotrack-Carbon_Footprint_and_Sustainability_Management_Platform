import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { Auth } from '../auth/auth';
import { NotificationsService } from './notifications/notifications.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './home.html',
  styleUrls: ['./home.css']
})
export class Home implements OnInit, OnDestroy {
  // Navigation sidebar drawer toggle state
  isSidebarOpen: boolean = false;

  // Sidebar "Notifications" badge count
  unreadCount = 0;
  private pollInterval?: any;

  constructor(
    private router: Router,
    private auth: Auth,
    private notificationsService: NotificationsService
  ) {}

  ngOnInit(): void {
    this.refreshUnreadCount();

    // Poll periodically so the badge stays roughly current even if the
    // user never leaves the current screen.
    this.pollInterval = setInterval(() => this.refreshUnreadCount(), 60000);

    // Also refresh right after navigating — cheaply catches the badge
    // clearing itself once the user has visited /home/notifications.
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => this.refreshUnreadCount());
  }

  ngOnDestroy(): void {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
    }
  }

  private refreshUnreadCount(): void {
    this.notificationsService.getUnreadCount().subscribe({
      next: (res) => (this.unreadCount = res?.count ?? 0),
      error: (err) => console.error('Failed to load unread notification count:', err)
    });
  }

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