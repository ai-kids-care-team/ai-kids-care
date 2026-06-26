import { apiClient } from './apiClient';

/**
 * 백엔드 `com.ai_kids_care.v1.type.EventStatusEnum` 과 동일한 문자열 집합.
 * detection event 의 상태 모델(단일 기준).
 */
export type EventStatusEnum =
  | 'OPEN'
  | 'ACKNOWLEDGED'
  | 'IN_REVIEW'
  | 'RESOLVED'
  | 'DISMISSED'
  | 'ESCALATED';

/**
 * 복合 확정 시 지정 가능한 결과 상태. 백엔드는 OPEN 을 거부하므로(`resultStatus` 非 OPEN) 제외한다.
 */
export type ReviewResultStatus = Exclude<EventStatusEnum, 'OPEN'>;

/** 백엔드 `EventReviewCreateDTO`. reviewer/kindergarten 은 세션·이벤트에서 派生되므로 보내지 않는다. */
export type EventReviewCreateDTO = {
  /** 검토 대상 detection event id(필수) */
  eventId: number;
  /** 검토 결과 상태(필수, OPEN 제외) */
  resultStatus: ReviewResultStatus;
  /** 선택: 검토 코멘트 */
  comment?: string;
  /** 선택: 공용 공간 이벤트에서 영향받는 아동 id 목록(비면 관계 그래프 자동 해석) */
  affectedChildIds?: number[];
  /** 선택: RESOLVED 시 보호자 통지 여부(ESCALATED 는 항상 통지) */
  notifyGuardians?: boolean;
};

/** 백엔드 `EventReviewVO`(201 응답). */
export type EventReviewVO = {
  reviewId: number;
  eventId: number;
  kindergartenId: number | null;
  userId: number | null;
  fromStatus: string | null;
  resultStatus: string;
  comment: string | null;
  createdAt: string;
};

/**
 * 검토(이벤트 리뷰) 확정. 기존 `POST /api/v1/event_reviews` 엔드포인트를 재사용한다.
 * CSRF 는 apiClient 인터셉터가 자동 처리하며, 인증은 ADMIN+TEACHER 가 백엔드 권위.
 */
export async function confirmEventReview(dto: EventReviewCreateDTO): Promise<EventReviewVO> {
  const res = await apiClient.post<EventReviewVO>('/event_reviews', dto);
  return res.data;
}
