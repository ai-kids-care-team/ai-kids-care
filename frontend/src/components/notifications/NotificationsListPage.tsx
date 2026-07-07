'use client';

import { NotificationsListForm } from './NotificationsListForm';
import { useNotifications } from '@/components/notifications/functions/useNotifications';

export function NotificationsListPage() {
  const { notifications, unreadCount, loading, error, markAsRead } = useNotifications();
  return (
    <NotificationsListForm
      notifications={notifications}
      unreadCount={unreadCount}
      loading={loading}
      error={error}
      onOpenNotification={markAsRead}
    />
  );
}
