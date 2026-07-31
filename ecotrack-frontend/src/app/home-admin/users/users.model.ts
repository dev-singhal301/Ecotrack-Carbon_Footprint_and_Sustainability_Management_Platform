// Matches GET /api/admin/users exactly, per AdminUserResponse.java.
export interface AdminUser {
  id: number;
  name: string;
  email: string;
  role: string; // "ROLE_ADMIN" or "ROLE_USER"
  enabled: boolean;
  currentXp: number;
  createdAt: string; // ISO datetime string
}