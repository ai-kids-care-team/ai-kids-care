import { apiClient } from './apiClient';

export type EventReview = {
  id: number;
  result_status: string;
  comment: string | null;
  created_at?: string;
};

export async function getEventReviews(eventId: number): Promise<EventReview[]> {
  if (!Number.isFinite(eventId) || eventId <= 0) return [];

  const response = await apiClient.get<EventReview[] | { content?: EventReview[]; data?: EventReview[] }>(
    `/event_reviews/${eventId}/reviews`,
  );

  const payload = response.data;
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.content)) return payload.content;
  if (Array.isArray(payload?.data)) return payload.data;
  return [];
}

