import { apiClient } from './apiClient';

/**
 * 백엔드 PUSH 구독 자기관리 API 클라이언트.
 *
 * 백엔드 형태는 **Pushover** 다(브라우저 Web Push/VAPID 아님). 사용자는 자신의
 * Pushover user key 를 `address` 로 등록하며, Service Worker 는 필요 없다.
 * 모든 조작은 세션 신원에서 `user_id` 를 파생하므로 본인 구독만 보고/수정/삭제할 수 있다.
 *
 * 필드는 백엔드 DTO/VO 와 1:1 로 맞춘다:
 *  - `PushSubscriptionRegisterDTO`: provider(선택, 기본 PUSHOVER) / address(필수) / deviceLabel(선택)
 *  - `PushSubscriptionUpdateDTO` (patch): address / deviceLabel / status (null·blank = 변경 없음)
 *  - `PushSubscriptionVO`: address 는 **응답에 노출되지 않는다**(투여 비밀/신원).
 */

/** 백엔드 `PushProviderEnum` — 현재 Pushover 만 구현됨. */
export type PushProvider = 'PUSHOVER';

/** 백엔드 `StatusEnum` 중 구독에 쓰이는 라벨. */
export type PushSubscriptionStatus = 'ACTIVE' | 'PENDING' | 'DISABLED' | 'REJECTED';

/**
 * 백엔드 `PushSubscriptionVO` 응답.
 * `address`(Pushover user key)는 서버가 의도적으로 노출하지 않으므로 여기에도 없다.
 */
export type PushSubscriptionVO = {
  pushSubscriptionId: number;
  userId: number;
  provider: PushProvider | string;
  deviceLabel: string | null;
  status: PushSubscriptionStatus | string;
  lastVerifiedAt: string | null;
  createdAt: string | null;
};

/** 백엔드 `PushSubscriptionRegisterDTO` 와 필드명을 맞춘다. */
export type PushSubscriptionRegisterPayload = {
  /** 생략 시 백엔드가 PUSHOVER 로 기본 처리. */
  provider?: PushProvider;
  /** Pushover user key (필수). */
  address: string;
  /** 선택: 기기 라벨 / Pushover 기기명. */
  deviceLabel?: string;
};

/** 백엔드 `PushSubscriptionUpdateDTO` (patch) 와 필드명을 맞춘다. null·blank = 변경 없음. */
export type PushSubscriptionUpdatePayload = {
  /** 새 Pushover user key. */
  address?: string;
  /** 새 기기 라벨. */
  deviceLabel?: string;
  /** 새 상태(ACTIVE / DISABLED 등). */
  status?: PushSubscriptionStatus;
};

/** `POST /push_subscriptions` — 본인 PUSH 구독 등록(201). */
export async function registerPushSubscription(
  payload: PushSubscriptionRegisterPayload,
): Promise<PushSubscriptionVO> {
  const response = await apiClient.post<PushSubscriptionVO>('/push_subscriptions', payload);
  return response.data;
}

/** `GET /push_subscriptions` — 본인 구독 목록. */
export async function getOwnPushSubscriptions(): Promise<PushSubscriptionVO[]> {
  const response = await apiClient.get<PushSubscriptionVO[]>('/push_subscriptions');
  return response.data;
}

/** `PUT /push_subscriptions/{id}` — 본인 구독 patch 수정. */
export async function updatePushSubscription(
  id: number,
  payload: PushSubscriptionUpdatePayload,
): Promise<PushSubscriptionVO> {
  const response = await apiClient.put<PushSubscriptionVO>(`/push_subscriptions/${id}`, payload);
  return response.data;
}

/** `DELETE /push_subscriptions/{id}` — 본인 구독 해지(204). */
export async function deletePushSubscription(id: number): Promise<void> {
  await apiClient.delete(`/push_subscriptions/${id}`);
}
