import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationsService, AppNotification } from './notifications.service';

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
  filter: 'all' | 'unread' = 'all';

  constructor(
    private notificationsService: NotificationsService,
    private cdr: ChangeDetectorRef
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

  get visibleNotifications(): AppNotification[] {
    return this.filter === 'unread'
      ? this.notifications.filter(n => !n.read)
      : this.notifications;
  }

  get unreadCount(): number {
    return this.notifications.filter(n => !n.read).length;
  }

  setFilter(filter: 'all' | 'unread'): void {
    this.filter = filter;
  }

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

  // Different notification types get different icons/colors in the template.
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