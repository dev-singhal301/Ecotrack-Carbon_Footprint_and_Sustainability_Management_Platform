import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminService } from '../admin.service';
import { AdminUser } from './users.model';

/**
 * Admin user-management table: list registered users, deactivate accounts.
 * Deactivation is soft (User.enabled = false) on the backend — this view
 * never issues a hard delete. Self-deactivation is blocked by the backend
 * (409); that error surfaces inline via actionError rather than trying to
 * duplicate the "is this me" check on the frontend.
 *
 * Uses signals for state written inside HttpClient subscribe callbacks —
 * same reasoning/pattern as the admin Dashboard component: this app is
 * zoneless, so a plain field assignment inside .subscribe() would not
 * trigger a re-render.
 */
@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './users.html',
  styleUrl: './users.css',
})
export class Users implements OnInit {
  users = signal<AdminUser[]>([]);
  isLoading = signal(false);
  dataError = signal<string | null>(null);

  // Tracks which row currently has a deactivate request in flight, so only
  // that row's button shows a busy state instead of the whole table.
  pendingUserId = signal<number | null>(null);
  actionError = signal<string | null>(null);

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  // Also callable by HomeAdmin's router-outlet (activate) handler, same
  // pattern as the admin Dashboard component.
  loadUsers(): void {
    this.isLoading.set(true);
    this.dataError.set(null);

    this.adminService.getUsers().subscribe({
      next: (users) => {
        this.users.set(users);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Failed to load users:', err);
        this.dataError.set(err.error?.message || 'Could not load users. Please try again.');
        this.isLoading.set(false);
      }
    });
  }

  confirmDeactivate(user: AdminUser): void {
    if (!confirm(`Deactivate ${user.name} (${user.email})? They will no longer be able to log in.`)) {
      return;
    }
    this.deactivateUser(user);
  }

  confirmReactivate(user: AdminUser): void {
    if (!confirm(`Reactivate ${user.name} (${user.email})? They will be able to log in again.`)) {
      return;
    }
    this.reactivateUser(user);
  }

  private deactivateUser(user: AdminUser): void {
    this.pendingUserId.set(user.id);
    this.actionError.set(null);

    this.adminService.deactivateUser(user.id).subscribe({
      next: () => {
        // Update just this row in place rather than a full reload.
        this.users.update(list =>
          list.map(u => u.id === user.id ? { ...u, enabled: false } : u)
        );
        this.pendingUserId.set(null);
      },
      error: (err) => {
        console.error('Failed to deactivate user:', err);
        this.actionError.set(err.error?.message || 'Could not deactivate this user. Please try again.');
        this.pendingUserId.set(null);
      }
    });
  }

  private reactivateUser(user: AdminUser): void {
    this.pendingUserId.set(user.id);
    this.actionError.set(null);

    this.adminService.reactivateUser(user.id).subscribe({
      next: () => {
        this.users.update(list =>
          list.map(u => u.id === user.id ? { ...u, enabled: true } : u)
        );
        this.pendingUserId.set(null);
      },
      error: (err) => {
        console.error('Failed to reactivate user:', err);
        this.actionError.set(err.error?.message || 'Could not reactivate this user. Please try again.');
        this.pendingUserId.set(null);
      }
    });
  }
}