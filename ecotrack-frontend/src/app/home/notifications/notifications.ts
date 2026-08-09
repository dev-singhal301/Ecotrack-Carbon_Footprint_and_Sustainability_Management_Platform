import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { NotificationsService, AppNotification } from './notifications.service';

type ReadFilter = 'all' | 'unread';
type TypeFilter = 'all' | 'goal' | 'challenge' | 'leaderboard';

/** One dated bucket of notifications for the grouped list rendering. */
interface NotificationGroup {
  label: string;
  items: AppNotification[];
}

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notifications.html',
  styleUrl: './notifications.css',
})
export class Notifications implements OnInit {

  notifications: AppNotification[] = [];
  isLoading = false;
  dataError: string | null = null;

  // 'all' shows everything; 'unread' filters down to what still needs attention.
  filter: ReadFilter = 'all';

  // Independent of the read/unread filter above — lets someone isolate
  // just their goal updates, challenge updates, or leaderboard results.
  typeFilter: TypeFilter = 'all';

  // ── Pagination ── Rows here are compact (icon + two lines), similar
  // density to the admin tables, so 10 per page matches that convention.
  private readonly pageSize = 10;
  currentPage = 1;

  constructor(
    private notificationsService: NotificationsService,
    private cdr: ChangeDetectorRef,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadNotifications();
  }

  loadNotifications(): void {
    this.isLoading = true;
    this.dataError = null;

    this.notificationsService.getNotifications().subscribe({
      next: (data) => {
        this.notifications = data || [];
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load notifications:', err);
        this.dataError = 'Could not load your notifications.';
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  // ── Filtering ──

  private typeMatches(notification: AppNotification, filter: TypeFilter): boolean {
    switch (filter) {
      case 'goal': return notification.type.startsWith('GOAL_');
      case 'challenge': return notification.type.startsWith('CHALLENGE_');
      case 'leaderboard': return notification.type === 'LEADERBOARD_MONTHLY';
      default: return true;
    }
  }

  get filteredNotifications(): AppNotification[] {
    return this.notifications.filter(n =>
      (this.filter === 'unread' ? !n.read : true) &&
      this.typeMatches(n, this.typeFilter)
    );
  }

  get unreadCount(): number {
    return this.notifications.filter(n => !n.read).length;
  }

  setFilter(filter: ReadFilter): void {
    this.filter = filter;
    this.currentPage = 1;
  }

  setTypeFilter(typeFilter: TypeFilter): void {
    this.typeFilter = typeFilter;
    this.currentPage = 1;
  }

  // ── Pagination (over the filtered list, before grouping) ──

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredNotifications.length / this.pageSize));
  }

  get safeCurrentPage(): number {
    return Math.min(this.currentPage, this.totalPages);
  }

  get pageNumbers(): number[] {
    const total = this.totalPages;
    const current = this.safeCurrentPage;
    const windowSize = Math.min(3, total);

    let start = current - 1;
    let end = current + 1;

    if (start < 1) {
      start = 1;
      end = start + windowSize - 1;
    }
    if (end > total) {
      end = total;
      start = end - windowSize + 1;
    }

    return Array.from({ length: end - start + 1 }, (_, i) => start + i);
  }

  goToPage(page: number): void {
    this.currentPage = Math.min(Math.max(1, page), this.totalPages);
    this.cdr.markForCheck();
  }

  prevPage(): void {
    this.goToPage(this.safeCurrentPage - 1);
  }

  nextPage(): void {
    this.goToPage(this.safeCurrentPage + 1);
  }

  // ── Date grouping ── applied AFTER pagination, over just the current
  // page's items. The backend already returns notifications newest-first,
  // so each page's items are a contiguous slice in date order — meaning
  // the buckets below always come out as clean, non-interleaved chunks
  // (all of "Today" before any of "This Week", etc.) without needing to
  // re-sort anything here.
  get groupedNotifications(): NotificationGroup[] {
    const start = (this.safeCurrentPage - 1) * this.pageSize;
    const pageItems = this.filteredNotifications.slice(start, start + this.pageSize);

    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const startOfWeek = startOfToday - 6 * 24 * 60 * 60 * 1000; // today + previous 6 days

    const groups: NotificationGroup[] = [];
    const groupFor = (label: string): NotificationGroup => {
      let group = groups.find(g => g.label === label);
      if (!group) {
        group = { label, items: [] };
        groups.push(group);
      }
      return group;
    };

    for (const n of pageItems) {
      const createdTime = new Date(n.createdAt).getTime();
      const label = createdTime >= startOfToday
        ? 'Today'
        : createdTime >= startOfWeek
          ? 'This Week'
          : 'Earlier';
      groupFor(label).items.push(n);
    }

    return groups;
  }

  // ── Read state ──

  onMarkAsRead(notification: AppNotification, event?: Event): void {
    event?.stopPropagation();
    if (notification.read) return;

    // Optimistic update — the badge/list should feel instant.
    notification.read = true;

    this.notificationsService.markAsRead(notification.notificationId).subscribe({
      error: (err) => {
        console.error('Failed to mark notification as read:', err);
        notification.read = false; // roll back on failure
        this.cdr.markForCheck();
      }
    });
  }

  onMarkAllAsRead(): void {
    if (this.unreadCount === 0) return;

    const previouslyUnread = this.notifications.filter(n => !n.read);
    previouslyUnread.forEach(n => (n.read = true));

    this.notificationsService.markAllAsRead().subscribe({
      error: (err) => {
        console.error('Failed to mark all notifications as read:', err);
        previouslyUnread.forEach(n => (n.read = false)); // roll back on failure
        this.cdr.markForCheck();
      }
    });
  }

  // ── Click-to-navigate ──
  // The `type` field alone is enough to route to the right page and land
  // on the right tab — no stored reference id needed, since we're
  // pointing at the relevant history tab rather than scrolling to one
  // specific card.
  private routeFor(notification: AppNotification): { path: string; queryParams?: Record<string, string> } | null {
    switch (notification.type) {
      case 'GOAL_COMPLETED': return { path: '/home/goals', queryParams: { tab: 'COMPLETED' } };
      case 'GOAL_EXPIRED': return { path: '/home/goals', queryParams: { tab: 'EXPIRED' } };
      case 'CHALLENGE_COMPLETED': return { path: '/home/challenges', queryParams: { tab: 'COMPLETED' } };
      case 'CHALLENGE_EXPIRED': return { path: '/home/challenges', queryParams: { tab: 'EXPIRED' } };
      // Cancellation claws XP back from what were COMPLETED participations
      // — Expired is the closer landing spot of the two history tabs.
      case 'CHALLENGE_CANCELLED': return { path: '/home/challenges', queryParams: { tab: 'EXPIRED' } };
      case 'LEADERBOARD_MONTHLY': return { path: '/home/leaderboard' };
      default: return null; // unrecognized/INFO type — nothing sensible to navigate to
    }
  }

  /** Drives the "clickable" affordance in the template — kept separate
   *  from iconFor()/colorClassFor() rather than inferring it from those,
   *  since "has a default icon" and "has somewhere to navigate" are
   *  unrelated facts that happening to line up today would be a coincidence
   *  to rely on. */
  isNavigable(type: string): boolean {
    return this.routeFor({ type } as AppNotification) !== null;
  }

  onNotificationClick(notification: AppNotification): void {
    this.onMarkAsRead(notification);

    const target = this.routeFor(notification);
    if (!target) return;

    this.router.navigate([target.path], target.queryParams ? { queryParams: target.queryParams } : {});
  }

  // ── Display helpers ──

  iconFor(type: string): string {
    switch (type) {
      case 'GOAL_COMPLETED':
      case 'CHALLENGE_COMPLETED':
        return 'fa-solid fa-circle-check';
      case 'GOAL_EXPIRED':
      case 'CHALLENGE_EXPIRED':
        return 'fa-solid fa-hourglass-end';
      case 'CHALLENGE_CANCELLED':
        return 'fa-solid fa-ban';
      case 'LEADERBOARD_MONTHLY':
        return 'fa-solid fa-ranking-star';
      default:
        return 'fa-solid fa-bell';
    }
  }

  colorClassFor(type: string): string {
    switch (type) {
      case 'GOAL_COMPLETED':
      case 'CHALLENGE_COMPLETED':
        return 'icon-success';
      case 'GOAL_EXPIRED':
      case 'CHALLENGE_EXPIRED':
      case 'CHALLENGE_CANCELLED':
        return 'icon-muted';
      case 'LEADERBOARD_MONTHLY':
        return 'icon-info';
      default:
        return 'icon-default';
    }
  }

  timeAgo(createdAt: string): string {
    const then = new Date(createdAt).getTime();
    const now = Date.now();
    const diffMs = Math.max(0, now - then);

    const minutes = Math.floor(diffMs / 60000);
    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes}m ago`;

    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;

    const days = Math.floor(hours / 24);
    if (days < 7) return `${days}d ago`;

    return new Date(createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }
}