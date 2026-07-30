import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminService } from '../admin.service';
import { AdminDashboardSummary } from './dashboard.model';

/**
 * Admin Dashboard skeleton. Real, backend-verified user counts — no
 * placeholder/fake numbers. Challenge & participation stats are added
 * once the Challenge entity/service work exists (see AdminService).
 *
 * Uses signals for state written inside the HttpClient subscribe callback.
 * This app runs zoneless (no zone.js — see angular.json/package.json), so a
 * plain field assignment inside .subscribe() does NOT trigger a re-render;
 * only signal writes, template-bound events, or the async pipe do. Same
 * pattern already used in auth-form.ts (isSubmitting.set(...)).
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css'],
})
export class Dashboard implements OnInit {
  summary = signal<AdminDashboardSummary | null>(null);
  isLoading = signal(false);
  dataError = signal<string | null>(null);

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadSummary();
  }

  // Also callable by HomeAdmin's router-outlet (activate) handler, so
  // navigating here without a full page reload still fetches fresh data.
  loadSummary(): void {
    this.isLoading.set(true);
    this.dataError.set(null);

    this.adminService.getDashboardSummary().subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Failed to load admin dashboard summary:', err);
        this.dataError.set(err.error?.message || 'Could not load the admin dashboard. Please try again.');
        this.isLoading.set(false);
      }
    });
  }
}

export { Dashboard as AdminDashboardComponent };