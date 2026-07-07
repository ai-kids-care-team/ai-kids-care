import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { NotificationReadVO } from '@/services/apis/notifications.api';

const getNotificationsMock = vi.fn();
const markReadMock = vi.fn();
const getUnreadCountMock = vi.fn();
const dispatchMock = vi.fn();

vi.mock('@/services/apis/notifications.api', async () => {
  const actual = await vi.importActual<typeof import('@/services/apis/notifications.api')>(
    '@/services/apis/notifications.api',
  );
  return {
    ...actual,
    getNotifications: (...args: unknown[]) => getNotificationsMock(...args),
    markRead: (...args: unknown[]) => markReadMock(...args),
    getUnreadCount: (...args: unknown[]) => getUnreadCountMock(...args),
  };
});

vi.mock('@/store/hook', () => ({
  useAppDispatch: () => dispatchMock,
}));

const { useNotifications } = await import('./useNotifications');
const { decrementUnreadNotificationCount } = await import('@/store/slices/userSlice');

function makeNotification(overrides: Partial<NotificationReadVO> = {}): NotificationReadVO {
  return {
    notificationId: 1,
    title: 't',
    body: 'b',
    status: 'SENT',
    readAt: null,
    createdAt: '2026-07-06T00:00:00+09:00',
    ...overrides,
  };
}

/**
 * wire-notification-read-state (6.2 optimistic-update slice): 알림 카드를 열람하면
 * (1) 로컬 상태의 `readAt` 이 즉시(낙관적) 채워지고, (2) ambient 배지 감소 액션이 1회 dispatch 되고,
 * (3) 서버에 `PATCH /{id}/read` 가 호출된다. 이미 읽은 알림 재클릭은 셋 다 no-op 이어야 한다
 * (중복 감소 방지 회귀 가드).
 */
describe('useNotifications markAsRead', () => {
  beforeEach(() => {
    getNotificationsMock.mockReset();
    markReadMock.mockReset();
    getUnreadCountMock.mockReset();
    dispatchMock.mockReset();
    markReadMock.mockResolvedValue(undefined);
    getUnreadCountMock.mockResolvedValue(0);
  });

  it('optimistically sets readAt, dispatches one decrement, and calls markRead for an unread notification', async () => {
    getNotificationsMock.mockResolvedValue([makeNotification({ notificationId: 5, readAt: null })]);
    const { result } = renderHook(() => useNotifications());
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.unreadCount).toBe(1);

    act(() => {
      result.current.markAsRead(5);
    });

    expect(result.current.notifications[0].readAt).not.toBeNull();
    expect(result.current.unreadCount).toBe(0);
    expect(dispatchMock).toHaveBeenCalledTimes(1);
    expect(dispatchMock).toHaveBeenCalledWith(decrementUnreadNotificationCount());
    expect(markReadMock).toHaveBeenCalledWith(5);
  });

  it('is a no-op (no dispatch, no PATCH) when the notification is already read', async () => {
    getNotificationsMock.mockResolvedValue([
      makeNotification({ notificationId: 9, readAt: '2026-07-06T01:00:00+09:00' }),
    ]);
    const { result } = renderHook(() => useNotifications());
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      result.current.markAsRead(9);
    });

    expect(dispatchMock).not.toHaveBeenCalled();
    expect(markReadMock).not.toHaveBeenCalled();
  });

  it('rolls back optimistic readAt and re-syncs the badge from the server when markRead fails', async () => {
    // ADMIN 이 본인 소유가 아닌 알림을 열면 PATCH 가 숨김 404 → 실패한다.
    // 롤백이 없으면 배지가 실제보다 낮게 표시된 채 서버와 어긋난다(code-review finding).
    getNotificationsMock.mockResolvedValue([makeNotification({ notificationId: 7, readAt: null })]);
    markReadMock.mockRejectedValue(new Error('404'));
    getUnreadCountMock.mockResolvedValue(3); // 서버 실제 미읽음 수
    const { setUnreadNotificationCount } = await import('@/store/slices/userSlice');
    const { result } = renderHook(() => useNotifications());
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      result.current.markAsRead(7);
    });

    // 낙관적 readAt 원복
    await waitFor(() => expect(result.current.notifications[0].readAt).toBeNull());
    expect(result.current.unreadCount).toBe(1);
    // 서버 값으로 배지 재동기화
    await waitFor(() =>
      expect(dispatchMock).toHaveBeenCalledWith(setUnreadNotificationCount(3)),
    );
    expect(getUnreadCountMock).toHaveBeenCalled();
  });

  it('is a no-op for an unknown id', async () => {
    getNotificationsMock.mockResolvedValue([makeNotification({ notificationId: 1, readAt: null })]);
    const { result } = renderHook(() => useNotifications());
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      result.current.markAsRead(999);
    });

    expect(dispatchMock).not.toHaveBeenCalled();
    expect(markReadMock).not.toHaveBeenCalled();
  });
});
