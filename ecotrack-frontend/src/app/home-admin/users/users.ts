import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../admin.service';
import { AdminUser } from './users.model';

export type RoleFilter = 'ALL' | 'ROLE_ADMIN' | 'ROLE_USER';
export type StatusFilter = 'ALL' | 'ACTIVE' | 'DISABLED';
export type XpSort = 'NONE' | 'ASC' | 'DESC';

/**
 * Admin user-management table: list registered users, deactivate accounts.
 * Deactivation is soft (User.enabled = false) on the backend — this view
 * never issues a hard delete. Self-deactivation is blocked by the backend
 * (409); that error surfaces inline via actionError rather than trying to
 * duplicate the "is this me" check on the frontend.
 *
 * Filtering/search/sort/pagination are all client-side over the
 * already-fetched list — GET /api/admin/users returns everyone in one
 * call, so there's no reason to round-trip to the backend for what's
 * just narrowing/paging data already in memory. `filteredUsers` derives
 * from `users` + the filter/sort signals; `pagedUsers` slices 10 rows
 * out of that for display, and both re-derive automatically whenever
 * anything upstream changes.
 *
 * Uses signals for state written inside HttpClient subscribe callbacks —
 * same reasoning/pattern as the admin Dashboard component: this app is
 * zoneless, so a plain field assignment inside .subscribe() would not
 * trigger a re-render.
 */
@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
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

  // ── Filters ──
  searchQuery = signal('');
  roleFilter = signal<RoleFilter>('ALL');
  statusFilter = signal<StatusFilter>('ALL');
  joinedFrom = signal('');  // YYYY-MM-DD, inclusive, empty = no lower bound
  joinedTo = signal('');    // YYYY-MM-DD, inclusive, empty = no upper bound
  xpSort = signal<XpSort>('NONE');

  filteredUsers = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const role = this.roleFilter();
    const status = this.statusFilter();
    const from = this.joinedFrom();
    const to = this.joinedTo();
    const sort = this.xpSort();

    let result = this.users().filter(user => {
      if (query && !user.name.toLowerCase().includes(query)) {
        return false;
      }
      if (role !== 'ALL' && user.role !== role) {
        return false;
      }
      if (status !== 'ALL') {
        const isActive = status === 'ACTIVE';
        if (user.enabled !== isActive) {
          return false;
        }
      }
      if (!this.withinJoinedRange(user.createdAt, from, to)) {
        return false;
      }
      return true;
    });

    if (sort === 'NONE') {
      // Default order: alphabetical by name, email as tiebreaker for
      // identical names — not whatever order the backend happened to
      // return (currently newest-first). Copy before sorting — never
      // mutate the array a computed signal reads from.
      result = [...result].sort((a, b) => {
        const byName = a.name.localeCompare(b.name);
        return byName !== 0 ? byName : a.email.localeCompare(b.email);
      });
    } else {
      result = [...result].sort((a, b) =>
        sort === 'ASC' ? a.currentXp - b.currentXp : b.currentXp - a.currentXp
      );
    }

    return result;
  });

  // Either bound can be left empty — "joined after X" and "joined before Y"
  // both work as one-sided filters, not just a fully-specified range.
  // `to` is treated as inclusive of the whole day (23:59:59.999), so
  // picking the same date for both fields still matches that day's signups.
  private withinJoinedRange(createdAt: string, from: string, to: string): boolean {
    const joinedDate = new Date(createdAt);

    if (from) {
      const fromDate = new Date(`${from}T00:00:00`);
      if (joinedDate < fromDate) {
        return false;
      }
    }
    if (to) {
      const toDate = new Date(`${to}T23:59:59.999`);
      if (joinedDate > toDate) {
        return false;
      }
    }
    return true;
  }

  resetFilters(): void {
    this.searchQuery.set('');
    this.roleFilter.set('ALL');
    this.statusFilter.set('ALL');
    this.joinedFrom.set('');
    this.joinedTo.set('');
    this.xpSort.set('NONE');
    this.currentPage.set(1);
  }

  // ── Pagination ── 10 users per page. currentPage is the raw signal
  // driven by the Prev/Next/page-number buttons; safeCurrentPage clamps
  // it to a valid range whenever filtering shrinks the result set (e.g.
  // you're on page 3, then a search narrows results to one page — this
  // snaps both the displayed page number and the rows back to page 1
  // automatically, without needing to intercept every individual filter
  // setter to reset currentPage by hand).
  private readonly pageSize = 10;
  currentPage = signal(1);

  totalPages = computed(() =>
    Math.max(1, Math.ceil(this.filteredUsers().length / this.pageSize))
  );

  safeCurrentPage = computed(() =>
    Math.min(this.currentPage(), this.totalPages())
  );

  // Plain [1, 2, 3, ...] for the page-number buttons — built here rather
  // than with an array-constructor trick in the template, since that's
  // fragile to read and easy to get wrong on a re-edit.
  pageNumbers = computed(() =>
    Array.from({ length: this.totalPages() }, (_, i) => i + 1)
  );

  pagedUsers = computed(() => {
    const start = (this.safeCurrentPage() - 1) * this.pageSize;
    return this.filteredUsers().slice(start, start + this.pageSize);
  });

  goToPage(page: number): void {
    this.currentPage.set(Math.min(Math.max(1, page), this.totalPages()));
  }

  prevPage(): void {
    this.goToPage(this.safeCurrentPage() - 1);
  }

  nextPage(): void {
    this.goToPage(this.safeCurrentPage() + 1);
  }

  // Two-letter avatar initials from a display name, e.g. "Dev Singhal" -> "DS".
  // Falls back to the first letter alone for single-word names.
  initials(name: string): string {
    const parts = name.trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
    return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
  }

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