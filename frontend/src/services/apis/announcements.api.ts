import { apiClient } from './apiClient';

export type AnnouncementStatusOption = {
  code: 'ACTIVE' | 'PENDING' | 'DISABLED';
  codeName: string;
  sortOrder: number;
};

export type AnnouncementSummary = {
  id: number;
  title: string;
  body?: string | null;
  pinned: boolean;
  viewCount: number;
  publishedAt: string | null;
  createdAt: string;
  /** 목록에서는 보통 생략(null) */
  status?: string | null;
  pinnedUntil?: string | null;
  startsAt?: string | null;
  endsAt?: string | null;
};

/** Spring Data `Page` JSON (목록 API) */
export type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
};

/** 공지 목록 페이지 크기(백엔드 `PageableDefault`와 맞춤) */
export const ANNOUNCEMENTS_LIST_PAGE_SIZE = 5;

export type GetAnnouncementsParams = {
  keyword?: string;
  /** 0부터 */
  page?: number;
  size?: number;
};

export type AnnouncementDetail = {
  id: number;
  title: string;
  body: string;
  pinned: boolean;
  viewCount: number;
  publishedAt: string | null;
  createdAt: string;
  status?: string | null;
  pinnedUntil?: string | null;
  startsAt?: string | null;
  endsAt?: string | null;
};

export type AnnouncementEdit = {
  id: number;
  title: string;
  body: string;
  pinned: boolean;
  pinnedUntil: string | null;
  /** 상세 API에 없을 수 있음 → 폼에서는 메타 기본값 유지 */
  status?: 'ACTIVE' | 'PENDING' | 'DISABLED';
  publishedAt: string | null;
  startsAt: string | null;
  endsAt: string | null;
};

export type AnnouncementMeta = {
  canWrite: boolean;
  statusOptions: AnnouncementStatusOption[];
};

/**
 * 백엔드 `AnnouncementCreateDTO` / `AnnouncementUpdateDTO`와 필드명을 맞춘다.
 * (`Boolean isPinned` → JSON `isPinned`, `pinned` 아님)
 */
export type AnnouncementWritePayload = {
  title: string;
  body: string;
  isPinned: boolean;
  pinnedUntil: string | null;
  status: 'ACTIVE' | 'PENDING' | 'DISABLED';
  publishedAt: string | null;
  startsAt: string | null;
  endsAt: string | null;
  /** 등록 시 백엔드 `Announcement` @NotNull — 조회수는 서버에서 설정 */
  createdAt?: string;
  updatedAt?: string;
  authorId?: number;
};

/** 등록 요청 직전 검증 (createdAt / updatedAt만 — viewCount는 백엔드에서 설정) */
export function validateAnnouncementCreateAuditFields(
  createdAt: string | null | undefined,
  updatedAt: string | null | undefined,
): string | null {
  if (createdAt == null || String(createdAt).trim() === '') {
    return '생성일시(createdAt)는 필수입니다.';
  }
  if (updatedAt == null || String(updatedAt).trim() === '') {
    return '수정일시(updatedAt)는 필수입니다.';
  }
  return null;
}

/** @deprecated AnnouncementWritePayload 사용 */
export type CreateAnnouncementPayload = AnnouncementWritePayload;

/** 백엔드 `AnnouncementVO` (생성/수정 응답) */
export type AnnouncementRecord = {
  id: number;
  authorId: number | null;
  title: string;
  body: string;
  isPinned: boolean | null;
  pinnedUntil: string | null;
  status: string | null;
  publishedAt: string | null;
  startsAt: string | null;
  endsAt: string | null;
  viewCount: number | null;
  createdAt: string | null;
  updatedAt: string | null;
  deletedAt: string | null;
  pinned: boolean;
};

/** 백엔드 `GET /announcements` → `Page<AnnouncementSummaryVO>` */
export async function getAnnouncements(
  params?: GetAnnouncementsParams,
): Promise<PageResponse<AnnouncementSummary>> {
  const page = params?.page ?? 0;
  const size = params?.size ?? ANNOUNCEMENTS_LIST_PAGE_SIZE;
  const keyword = params?.keyword?.trim();
  const response = await apiClient.get<PageResponse<AnnouncementSummary>>('/announcements', {
    params: {
      page,
      size,
      ...(keyword ? { keyword } : {}),
    },
  });
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

export async function createAnnouncement(payload: AnnouncementWritePayload) {
  const response = await apiClient.post<AnnouncementRecord>('/announcements', payload);
  return response.data;
}

function parseAnnouncementStatus(
  raw: string | null | undefined,
): 'ACTIVE' | 'PENDING' | 'DISABLED' | undefined {
  if (raw == null || raw === '') return undefined;
  const u = String(raw).toUpperCase();
  if (u === 'ACTIVE' || u === 'PENDING' || u === 'DISABLED') return u;
  return undefined;
}

/** 상세 `GET /announcements/{id}` 응답으로 수정 폼 초기값 구성 */
export async function getAnnouncementForEdit(id: number): Promise<AnnouncementEdit> {
  const d = await getAnnouncementDetail(id);
  return {
    id: d.id,
    title: d.title,
    body: d.body,
    pinned: d.pinned,
    pinnedUntil: d.pinnedUntil ?? null,
    status: parseAnnouncementStatus(d.status),
    publishedAt: d.publishedAt,
    startsAt: d.startsAt ?? null,
    endsAt: d.endsAt ?? null,
  };
}

export async function updateAnnouncement(id: number, payload: AnnouncementWritePayload) {
  const response = await apiClient.put<AnnouncementRecord>(`/announcements/${id}`, payload);
  return response.data;
}

export async function deleteAnnouncement(id: number): Promise<void> {
  await apiClient.delete(`/announcements/${id}`);
}
