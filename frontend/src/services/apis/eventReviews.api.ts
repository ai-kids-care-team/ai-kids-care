import { apiClient } from './apiClient';

export type EventReview = {
  id: number;
  user_id?: number | null;
  result_status: string;
  comment: string | null;
  created_at?: string;
};

export type CreateEventReviewDTO = {
  event_id: number;
  user_id: string;
  from_status: string | null;
  result_status: 'ACKNOWLEDGED' | 'IN_REVIEW' | 'RESOLVED' | 'DISMISSED' | 'ESCALATED';
  comment: string | null;
};

function normalizeEventReview(raw: any): EventReview | null {
  if (!raw) return null;

  const id = raw.id ?? raw.review_id ?? raw.reviewId;
  if (!Number.isFinite(id)) return null;

  const userId = raw.user_id ?? raw.userId ?? null;
  const resultStatus: string =
    raw.result_status ?? raw.resultStatus ?? raw.status ?? raw.result_status_code ?? '';
  const comment: string | null = raw.comment ?? raw.review_comment ?? raw.reviewComment ?? null;
  const createdAt: string | undefined = raw.created_at ?? raw.createdAt ?? undefined;

  return {
    id,
    user_id: userId,
    result_status: String(resultStatus),
    comment,
    created_at: createdAt,
  };
}

export async function getEventReviews(eventId: number): Promise<EventReview[]> {
  if (!Number.isFinite(eventId) || eventId <= 0) return [];

  const response = await apiClient.get<EventReview[] | { content?: EventReview[]; data?: EventReview[] }>(
    `/event_reviews/${eventId}/reviews`,
  );

  const payload = response.data;
  const list: any[] = Array.isArray(payload)
    ? payload
    : Array.isArray((payload as any)?.content)
      ? (payload as any).content
      : Array.isArray((payload as any)?.data)
        ? (payload as any).data
        : [];

  return list
    .map((item) => normalizeEventReview(item))
    .filter((item): item is EventReview => item !== null);
}

export async function getLatestEventReview(eventId: number): Promise<EventReview | null> {
  if (!Number.isFinite(eventId) || eventId <= 0) return null;

  const response = await apiClient.get<EventReview | { data?: EventReview }>(
    `/event_reviews/${eventId}/reviews/latest`,
  );

  const payload: any = response.data;
  if (!payload) return null;

  const candidate = payload.data ?? payload;
  const normalized = normalizeEventReview(candidate);
  return normalized ?? null;
}

export async function createEventReview(dto: CreateEventReviewDTO): Promise<EventReview> {
  const { event_id } = dto;
  if (!Number.isFinite(event_id) || event_id <= 0) {
    throw new Error('유효하지 않은 event_id 입니다.');
  }

  const payload = {
    event_id: dto.event_id,
    user_id: dto.user_id,
    from_status: dto.from_status,
    result_status: dto.result_status,
    comment: dto.comment,
  };

  const response = await apiClient.post<EventReview | { data?: EventReview }>(`/event_reviews`, payload);

  const body: any = response.data;
  const candidate = body?.data ?? body;
  const normalized = normalizeEventReview(candidate);
  if (!normalized) {
    throw new Error('이벤트 리뷰 생성 응답을 해석할 수 없습니다.');
  }
  return normalized;
}

