'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  getNotifications,
  isNotificationUnread,
  markRead,
  NotificationReadVO,
} from '@/services/apis/notifications.api';
import { useAppDispatch } from '@/store/hook';
import { decrementUnreadNotificationCount } from '@/store/slices/userSlice';

export function useNotifications() {
  const dispatch = useAppDispatch();
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

  /**
   * wire-notification-read-state D5: 알림 1건 열람 시 낙관적으로 `readAt` 을 채우고
   * ambient 배지(Redux `user.unreadNotificationCount`)를 감소시킨 뒤
   * `PATCH /notifications/{id}/read` 를 보낸다(멱등, 실패해도 롤백하지 않음 — 다음
   * 세션/새로고침에서 서버 값으로 자연 수렴).
   * 이미 읽은 알림에 대한 재호출은 no-op(중복 감소 방지).
   */
  const markAsRead = useCallback(
    (id: number) => {
      const target = notifications.find((n) => n.notificationId === id);
      if (!target || target.readAt != null) {
        return;
      }

      dispatch(decrementUnreadNotificationCount());
      setNotifications((prev) =>
        prev.map((n) =>
          n.notificationId === id ? { ...n, readAt: new Date().toISOString() } : n,
        ),
      );

      void markRead(id).catch((e) => {
        console.error('알림 읽음 처리 실패:', e);
      });
    },
    [notifications, dispatch],
  );

  return {
    notifications,
    unreadCount,
    loading,
    error,
    markAsRead,
  };
}
