'use client';

import { useEffect, useState } from 'react';
import {
  getNotifications,
  isNotificationUnread,
  NotificationReadVO,
} from '@/services/apis/notifications.api';

export function useNotifications() {
  const [notifications, setNotifications] = useState<NotificationReadVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const data = await getNotifications();
        // 최신순 정렬 (createdAt 내림차순)
        const sorted = [...data].sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
        );
        setNotifications(sorted);
      } catch (e) {
        console.error('알림 목록 조회 실패:', e);
        setError('알림 목록을 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, []);

  const unreadCount = notifications.filter(isNotificationUnread).length;

  return {
    notifications,
    unreadCount,
    loading,
    error,
  };
}
