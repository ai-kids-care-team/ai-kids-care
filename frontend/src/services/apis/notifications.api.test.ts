import { describe, expect, it } from 'vitest';
import { isNotificationUnread, type NotificationReadVO } from './notifications.api';

/**
 * INT-03 회귀 가드: 백엔드 `NotificationStatusEnum` 8값 각각에 대해
 * isNotificationUnread()의 판정을 개별 단언한다.
 * QUEUED/SENDING/FAILED/DEFERRED = 미읽음, SENT/DELIVERED/READ/CANCELED = 읽음.
 * 이 allowlist가 회귀(예: 다시 `status !== 'SENT'` 방식으로 되돌아가는 등)하면
 * 아래 단언 중 하나 이상이 실패해야 한다.
 */
function notification(status: string): NotificationReadVO {
  return {
    notificationId: 1,
    title: 't',
    body: 'b',
    status,
    createdAt: '2026-07-06T00:00:00+09:00',
  };
}

describe('isNotificationUnread', () => {
  it.each(['QUEUED', 'SENDING', 'FAILED', 'DEFERRED'])(
    'treats %s as unread',
    (status) => {
      expect(isNotificationUnread(notification(status))).toBe(true);
    },
  );

  it.each(['SENT', 'DELIVERED', 'READ', 'CANCELED'])(
    'treats %s as read (not unread)',
    (status) => {
      expect(isNotificationUnread(notification(status))).toBe(false);
    },
  );

  it('treats an unrecognized status as read (fail-safe, not fail-open to unread)', () => {
    expect(isNotificationUnread(notification('SOME_FUTURE_STATUS'))).toBe(false);
  });
});
