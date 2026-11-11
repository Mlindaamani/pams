import { api, unwrap } from '@/api/client';
import type { Notification, Page } from '@/api/types';

export async function unreadCount(): Promise<{ unread: number }> {
  return unwrap(api.GET('/api/v1/notifications/unread-count')) as Promise<{ unread: number }>;
}

export async function listNotifications(params: {
  unread?: boolean;
  page?: number;
  size?: number;
}): Promise<Page<Notification>> {
  return unwrap(
    api.GET('/api/v1/notifications', {
      params: { query: { ...params, sort: 'createdAt,desc' } },
    }),
  ) as Promise<Page<Notification>>;
}

export async function markNotificationRead(id: string): Promise<Notification> {
  return unwrap(api.POST('/api/v1/notifications/{id}/read', { params: { path: { id } } }));
}

export async function markAllRead(): Promise<number> {
  const result = await unwrap(api.POST('/api/v1/notifications/read-all'));
  return result.updated ?? 0;
}
