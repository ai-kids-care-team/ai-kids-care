import { baseApi } from '@/services/apis/base.api';

/**
 * UX-08 통지 환경설정 — `openspec/changes/wire-notification-preferences/api-contract.md`
 * 냉동 계약과 필드 단위로 일치시킨다.
 *
 * 백엔드는 세션(userId + activeKindergartenId)에서 본인 canonical 행만 upsert 한다 —
 * 프론트는 kindergartenId 를 절대 보내지 않는다.
 */

/** 계약의 `NotificationPreferenceVO` — GET/PUT 응답 공통 shape. */
export type NotificationPreferenceVO = {
  /** 통지 총 스위치. 행이 없으면 백엔드가 기본값 true 로 응답한다(404 아님). */
  enabled: boolean;
  /** 정숙 시간대 시작 (`HH:mm`, Asia/Seoul 로컬시). 미설정 시 null. */
  quietHoursStart: string | null;
  /** 정숙 시간대 종료 (`HH:mm`). 미설정 시 null. */
  quietHoursEnd: string | null;
};

/**
 * 계약의 `NotificationPreferenceUpdateDTO`.
 * start/end 는 동시에 null(정숙 해제) 이거나 동시에 값이 있어야 한다(한쪽만 있으면 백엔드 400) —
 * 이 단측(單側) 검증은 프론트에서도 제출 전에 막는다(컴포넌트 쪽 책임).
 */
export type NotificationPreferenceUpdatePayload = {
  enabled: boolean;
  quietHoursStart: string | null;
  quietHoursEnd: string | null;
};

export const notificationPreferencesApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    /** `GET /api/v1/notification_rules/me` — 로그인 사용자 본인 통지 환경설정. */
    getMyNotificationPreference: build.query<NotificationPreferenceVO, void>({
      query: () => '/notification_rules/me',
      providesTags: ['NotificationPreference'],
    }),
    /** `PUT /api/v1/notification_rules/me` — 본인 환경설정 upsert. 응답은 반영된 값을 그대로 반환. */
    updateMyNotificationPreference: build.mutation<
      NotificationPreferenceVO,
      NotificationPreferenceUpdatePayload
    >({
      query: (body) => ({
        url: '/notification_rules/me',
        method: 'PUT',
        body,
      }),
      invalidatesTags: ['NotificationPreference'],
    }),
  }),
  overrideExisting: false,
});

export const {
  useGetMyNotificationPreferenceQuery,
  useUpdateMyNotificationPreferenceMutation,
} = notificationPreferencesApi;
