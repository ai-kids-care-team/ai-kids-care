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

type EventReviewApiRow = {
  id?: unknown;
  review_id?: unknown;
  reviewId?: unknown;
  user_id?: unknown;
  userId?: unknown;
  result_status?: unknown;
  resultStatus?: unknown;
  status?: unknown;
  result_status_code?: unknown;
  comment?: unknown;
  review_comment?: unknown;
  reviewComment?: unknown;
  created_at?: unknown;
  createdAt?: unknown;
};

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : null;
}

function normalizeEventReview(raw: unknown): EventReview | null {
  const record = asRecord(raw);
  if (!record) return null;

  const row = record as EventReviewApiRow;
  const id = Number(row.id ?? row.review_id ?? row.reviewId ?? NaN);
  if (!Number.isFinite(id)) return null;

  const userIdValue = row.user_id ?? row.userId;
  const userId =
    userIdValue == null
      ? null
      : Number.isFinite(Number(userIdValue))
        ? Number(userIdValue)
        : null;

  const resultStatusValue =
    row.result_status ?? row.resultStatus ?? row.status ?? row.result_status_code ?? '';
  const commentValue = row.comment ?? row.review_comment ?? row.reviewComment ?? null;
  const createdAtValue = row.created_at ?? row.createdAt;

  return {
    id,
    user_id: userId,
    result_status: String(resultStatusValue),
    comment: commentValue == null ? null : String(commentValue),
    created_at: createdAtValue == null ? undefined : String(createdAtValue),
  };
}

function extractListPayload(payload: unknown): unknown[] {
  if (Array.isArray(payload)) {
    return payload;
  }

  const record = asRecord(payload);
  if (!record) {
    return [];
  }

  if (Array.isArray(record.content)) {
    return record.content;
  }

  if (Array.isArray(record.data)) {
    return record.data;
  }

  return [];
}

function extractSinglePayload(payload: unknown): unknown {
  const record = asRecord(payload);
  return record && 'data' in record ? record.data : payload;
}

export async function getEventReviews(eventId: number): Promise<EventReview[]> {
  if (!Number.isFinite(eventId) || eventId <= 0) return [];

  const response = await apiClient.get<unknown>('/event_reviews', {
    params: {
      eventId,
      page: 0,
      size: 100,
      sort: 'createdAt,asc',
    },
  });
  const list = extractListPayload(response.data);

  return list
    .map((item) => normalizeEventReview(item))
    .filter((item): item is EventReview => item !== null);
}

export async function getLatestEventReview(eventId: number): Promise<EventReview | null> {
  if (!Number.isFinite(eventId) || eventId <= 0) return null;

  const response = await apiClient.get<unknown>(`/event_reviews/${eventId}/latest`);
  const normalized = normalizeEventReview(extractSinglePayload(response.data));
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

  const response = await apiClient.post<unknown>(`/event_reviews`, payload);
  const normalized = normalizeEventReview(extractSinglePayload(response.data));
  if (!normalized) {
      throw new Error('이벤트 리뷰 생성 응답을 해석할 수 없습니다.');
  }
  return normalized;
}
