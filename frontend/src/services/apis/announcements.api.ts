import { apiClient } from './apiClient';

export type AnnouncementStatusOption = {
  code: 'ACTIVE' | 'PENDING' | 'DISABLED';
  codeName: string;
  sortOrder: number;
};

export type AnnouncementSummary = {
  id: number;
  title: string;
  pinned: boolean;
  viewCount: number;
  publishedAt: string | null;
  createdAt: string;
};

export type AnnouncementDetail = {
  id: number;
  title: string;
  body: string;
  viewCount: number;
  publishedAt: string | null;
  createdAt: string;
};

export type AnnouncementMeta = {
  canWrite: boolean;
  statusOptions: AnnouncementStatusOption[];
};

export type CreateAnnouncementPayload = {
  title: string;
  body: string;
  pinned: boolean;
  pinnedUntil: string | null;
  status: 'ACTIVE' | 'PENDING' | 'DISABLED';
  publishedAt: string | null;
  startsAt: string | null;
  endsAt: string | null;
};

export async function getAnnouncements() {
  const response = await apiClient.get<AnnouncementSummary[]>('/announcements');
  return response.data;
}

export async function getAnnouncementsMeta() {
  const response = await apiClient.get<AnnouncementMeta>('/announcements/meta');
  return response.data;
}

export async function getAnnouncementDetail(id: number) {
  const response = await apiClient.get<AnnouncementDetail>(`/announcements/${id}`);
  return response.data;
}

export async function createAnnouncement(payload: CreateAnnouncementPayload) {
  const response = await apiClient.post<{ id: number; message: string }>('/announcements', payload);
  return response.data;
}
