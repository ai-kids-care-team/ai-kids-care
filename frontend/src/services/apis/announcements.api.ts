import { apiClient } from './apiClient';

export type AnnouncementStatusOption = {
  code: 'ACTIVE' | 'PENDING' | 'DISABLED';
  codeName: string;
  sortOrder: number;
};

/** 백엔드 `AnnouncementVO` 기반 목록 아이템 */
export type AnnouncementListItem = {
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

/** 공지 목록·홈 미리보기 등 한 번에 가져올 기본 건수 */
export const ANNOUNCEMENTS_LIST_PAGE_SIZE = 7;
export const ANNOUNCEMENTS_DEFAULT_SORT = ['status,asc', 'isPinned,desc', 'publishedAt,desc'] as const;

export type GetAnnouncementsParams = {
  keyword?: string;
  /** 0부터 */
  page?: number;
  size?: number;
  /** Spring Pageable sort 형식 예: 'createdAt,desc' 또는 ['isPinned,desc', 'publishedAt,desc'] */
  sort?: string | string[];
};

export type AnnouncementDetail = AnnouncementListItem;

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

/** 메타 API 대신 작성/수정 폼에서 사용하는 기본 상태 옵션 */
export const DEFAULT_ANNOUNCEMENT_STATUS_OPTIONS: AnnouncementStatusOption[] = [
  { code: 'PENDING', codeName: '대기', sortOrder: 1 },
  { code: 'ACTIVE', codeName: '게시', sortOrder: 2 },
  { code: 'DISABLED', codeName: '비활성', sortOrder: 3 },
];

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

/** 백엔드 `GET /announcements` → `Page<AnnouncementVO>` */
export async function getAnnouncements(
  params?: GetAnnouncementsParams,
): Promise<PageResponse<AnnouncementListItem>> {
  const page = params?.page ?? 0;
  const size = params?.size ?? ANNOUNCEMENTS_LIST_PAGE_SIZE;
  const keyword = params?.keyword?.trim();
  const sort = params?.sort;
  const response = await apiClient.get<PageResponse<AnnouncementListItem>>('/announcements', {
    params: {
      page,
      size,
      ...(keyword ? { keyword } : {}),
      ...(sort ? { sort } : {}),
    },
  });
  return response.data;
}

/**
 * 개발 모드(StrictMode)에서 동일 컴포넌트가 즉시 재마운트되며 같은 상세 API를
 * 중복 호출하는 경우가 있어, 같은 id의 진행 중 요청은 하나로 합친다.
 */
const announcementDetailInFlight = new Map<number, Promise<AnnouncementDetail>>();

export async function getAnnouncementDetail(id: number) {
  const inFlight = announcementDetailInFlight.get(id);
  if (inFlight) return inFlight;

  const request = apiClient
    .get<AnnouncementDetail>(`/announcements/${id}`)
    .then((response) => response.data)
    .finally(() => {
      announcementDetailInFlight.delete(id);
    });

  announcementDetailInFlight.set(id, request);
  return request;
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
