import { apiClient } from './apiClient';

/**
 * 백엔드 `NotificationReadVO` (SPEC-0001 / ADR-0018 A3d, wire-notification-read-state 로 `readAt` 추가) 와
 * 필드명·타입을 맞춘 최소 VO.
 * - notificationId, title, body, status, readAt, createdAt (6 필드)
 * - status: 백엔드 NotificationStatusEnum 문자열 그대로 — 배송 상태(전송 실패 등) 표시에만 사용, 미읽음 판정에는 미사용
 * - readAt: 본인이 이 알림을 읽은 시각(ISO 8601 offset-datetime). null = 미읽음. 배송 status 와 정교(orthogonal).
 */
export type NotificationReadVO = {
  notificationId: number;
  title: string;
  body: string;
  /** 백엔드 NotificationStatusEnum 문자열 그대로. FAILED 는 "전송 실패" 로만 표시하고 미읽음 판정과 무관하다. */
  status: string;
  /** 본인 열람 시각. null = 미읽음(wire-notification-read-state D1) */
  readAt: string | null;
  /** ISO 8601 offset-datetime (OffsetDateTime) */
  createdAt: string;
};

/**
 * 미읽음(unread) 기준: `readAt == null`. 배송 status 는 더 이상 미읽음 판정에 관여하지 않는다
 * (wire-notification-read-state D1 — 배송 상태와 열람 상태는 정교한 별도 축).
 */
export function isNotificationUnread(n: NotificationReadVO): boolean {
  return n.readAt == null;
}

/**
 * `PATCH /api/v1/notifications/{id}/read` — 본인 알림 읽음 처리(멱등, 200).
 * CSRF 헤더는 `apiClient` 인터셉터가 자동 주입 — 여기서 수동으로 붙이지 않는다.
 * 타인/타 테넌트 알림이면 백엔드가 404 로 존재 자체를 숨긴다.
 */
export async function markRead(id: number): Promise<void> {
  await apiClient.patch(`/notifications/${id}/read`);
}

/**
 * `GET /api/v1/notifications/unread-count` — 로그인 사용자 **본인**의 미읽음 개수.
 * KINDERGARTEN_ADMIN 도 전원 목록을 볼 수 있지만 이 카운트는 항상 본인 수신분만 집계한다(D4).
 */
export async function getUnreadCount(): Promise<number> {
  const response = await apiClient.get<{ unreadCount: number }>('/notifications/unread-count');
  return response.data.unreadCount;
}

/**
 * 진행 중 중복 요청 병합(StrictMode 재마운트 대응) — announcements.api.ts 패턴 동일.
 */
const notificationDetailInFlight = new Map<number, Promise<NotificationReadVO>>();

/** `GET /api/v1/notifications` — 인증 세션 기준 수신자의 전체 알림 목록 */
export async function getNotifications(): Promise<NotificationReadVO[]> {
  const response = await apiClient.get<NotificationReadVO[]>('/notifications');
  return response.data;
}

/** `GET /api/v1/notifications/{id}` — 단건 상세 */
export async function getNotificationDetail(id: number): Promise<NotificationReadVO> {
  const inFlight = notificationDetailInFlight.get(id);
  if (inFlight) return inFlight;

  const request = apiClient
    .get<NotificationReadVO>(`/notifications/${id}`)
    .then((res) => res.data)
    .finally(() => {
      notificationDetailInFlight.delete(id);
    });

  notificationDetailInFlight.set(id, request);
  return request;
}
