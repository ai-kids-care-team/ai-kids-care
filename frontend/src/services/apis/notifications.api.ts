import { apiClient } from './apiClient';

/**
 * 백엔드 `NotificationReadVO` (SPEC-0001 / ADR-0018 A3d) 와 필드명·타입을 맞춘 최소 VO.
 * - notificationId, title, body, status, createdAt (5 필드)
 * - status: 백엔드 NotificationStatusEnum 문자열 그대로 — 미읽음 판정에 사용
 */
export type NotificationReadVO = {
  notificationId: number;
  title: string;
  body: string;
  /** 백엔드 NotificationStatusEnum 문자열 그대로 */
  status: string;
  /** ISO 8601 offset-datetime (OffsetDateTime) */
  createdAt: string;
};

/**
 * 미읽음(unread) 기준: 아직 최종 전달/열람되지 않은 상태의 명시적 allowlist.
 * 백엔드 NotificationStatusEnum 8값: QUEUED, SENDING, SENT, DELIVERED, READ, FAILED, CANCELED, DEFERRED.
 * QUEUED/SENDING/FAILED/DEFERRED 는 미읽음으로 간주하고,
 * SENT/DELIVERED/READ/CANCELED 는 미읽음이 아니다.
 */
const UNREAD_NOTIFICATION_STATUSES = ['QUEUED', 'SENDING', 'FAILED', 'DEFERRED'];

export function isNotificationUnread(n: NotificationReadVO): boolean {
  return UNREAD_NOTIFICATION_STATUSES.includes(n.status);
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
