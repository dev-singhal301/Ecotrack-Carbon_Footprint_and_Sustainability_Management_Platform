import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AppNotification {
  notificationId: number;
  title: string;
  message: string;
  type: string;
  read: boolean;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationsService {
  private readonly baseUrl = `${environment.apiUrl || 'http://localhost:8080'}/api/notifications`;

  constructor(private http: HttpClient) {}

  /** Fetch every notification for the current user, newest first. */
  getNotifications(): Observable<AppNotification[]> {
    return this.http.get<AppNotification[]>(this.baseUrl);
  }

  /** Fetch just the unread count, for the sidebar badge. */
  getUnreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.baseUrl}/unread-count`);
  }

  markAsRead(notificationId: number): Observable<{ message: string }> {
    return this.http.patch<{ message: string }>(`${this.baseUrl}/${notificationId}/read`, {});
  }

  markAllAsRead(): Observable<{ message: string }> {
    return this.http.patch<{ message: string }>(`${this.baseUrl}/read-all`, {});
  }
}