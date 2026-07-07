import { describe, expect, it, vi, beforeEach } from 'vitest';

const getMock = vi.fn();
const patchMock = vi.fn();

vi.mock('./apiClient', () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    patch: (...args: unknown[]) => patchMock(...args),
  },
}));

const { isNotificationUnread, markRead, getUnreadCount } = await import('./notifications.api');

/**
 * INT-03 / wire-notification-read-state 회귀 가드:
 * 미읽음(unread) 판정은 이제 배송 status allowlist 가 아니라 `readAt == null` 기준이다
 * (D1 — 배송 상태와 열람 상태는 정교(orthogonal)한 별도 축). FAILED 도 더 이상 자동 미읽음이 아니다.
 */
type NotificationReadVOShape = {
  notificationId: number;
  title: string;
  body: string;
  status: string;
  readAt: string | null;
  createdAt: string;
};

function notification(overrides: Partial<NotificationReadVOShape> = {}): NotificationReadVOShape {
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

describe('isNotificationUnread', () => {
  it('treats a null readAt as unread regardless of delivery status', () => {
    for (const status of ['QUEUED', 'SENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED', 'CANCELED', 'DEFERRED']) {
      expect(isNotificationUnread(notification({ status, readAt: null }))).toBe(true);
    }
  });

  it('treats a non-null readAt as read regardless of delivery status', () => {
    for (const status of ['QUEUED', 'SENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED', 'CANCELED', 'DEFERRED']) {
      expect(
        isNotificationUnread(notification({ status, readAt: '2026-07-06T01:00:00+09:00' })),
      ).toBe(false);
    }
  });

  it('no longer treats a delivery-FAILED notification as automatically unread once readAt is set', () => {
    // 회귀 가드: 예전 allowlist(QUEUED/SENDING/FAILED/DEFERRED)가 되살아나면
    // FAILED + readAt 이 있어도 미읽음으로 잘못 판정된다.
    expect(isNotificationUnread(notification({ status: 'FAILED', readAt: '2026-07-06T01:00:00+09:00' }))).toBe(
      false,
    );
  });
});

describe('markRead', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('PATCHes the id-scoped /read path without manually attaching a CSRF header', async () => {
    patchMock.mockResolvedValue({ status: 200 });
    await markRead(42);
    expect(patchMock).toHaveBeenCalledWith('/notifications/42/read');
    // CSRF 헤더는 apiClient.ts 의 request 인터셉터가 전역으로 주입 — 여기서 호출부가
    // 두 번째 인자로 헤더를 조립해 넘기지 않는다는 사실 자체가 "수동으로 CSRF 를 붙이지 않는다"는 계약이다.
    expect(patchMock.mock.calls[0]).toHaveLength(1);
  });

  it('is a thin idempotent call — invoking it twice for the same id just PATCHes twice', async () => {
    patchMock.mockResolvedValue({ status: 200 });
    await markRead(7);
    await markRead(7);
    expect(patchMock).toHaveBeenCalledTimes(2);
    expect(patchMock).toHaveBeenNthCalledWith(1, '/notifications/7/read');
    expect(patchMock).toHaveBeenNthCalledWith(2, '/notifications/7/read');
  });
});

describe('getUnreadCount', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('GETs the unread-count endpoint and returns the raw count', async () => {
    getMock.mockResolvedValue({ data: { unreadCount: 3 } });
    const result = await getUnreadCount();
    expect(getMock).toHaveBeenCalledWith('/notifications/unread-count');
    expect(result).toBe(3);
  });

  it('returns zero as-is when the caller has no unread notifications', async () => {
    getMock.mockResolvedValue({ data: { unreadCount: 0 } });
    const result = await getUnreadCount();
    expect(result).toBe(0);
  });
});
