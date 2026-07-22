import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../auth.service';

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
    private authService: AuthService
  ) {}

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
    // Use AuthService logout to properly clean 'ecotrack_jwt_token' & 'ecotrack_user_email'
    this.authService.logout();
    this.router.navigate(['/auth']);
  }
}