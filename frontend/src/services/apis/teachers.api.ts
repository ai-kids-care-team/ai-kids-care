import { apiClient } from './apiClient';
import type { PageResponse } from './appreciationLetters.api';

export type Teacher = {
  teacherId: number | null;
  userId: number;
  name: string;
};

export type TeacherVO = {
  teacherId: number;
  kindergartenId: number;
  userId: number;
  name: string;
  gender: string | null;
  level: string | null;
  startDate: string | null;
  endDate: string | null;
  status: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type TeacherApiRow = TeacherVO & {
  id?: number;
  kindergarten_id?: number;
  user_id?: number;
  teacher_id?: number;
  start_date?: string | null;
  end_date?: string | null;
  created_at?: string | null;
  updated_at?: string | null;
};

function firstPositiveLong(...vals: unknown[]): number | undefined {
  for (const v of vals) {
    if (v === null || v === undefined || v === '') continue;
    const n = typeof v === 'number' ? v : Number(v);
    if (!Number.isFinite(n) || n <= 0) continue;
    return Math.trunc(n);
  }
  return undefined;
}

export type NormalizeTeacherOptions = { fallbackKindergartenId?: number };

function firstDefined(...vals: unknown[]): unknown {
  for (const v of vals) {
    if (v !== undefined) return v;
  }
  return undefined;
}

function nullableString(...vals: unknown[]): string | null {
  const value = firstDefined(...vals);
  return value == null ? null : String(value);
}

export function normalizeTeacherVO(
  raw: TeacherApiRow,
  options?: NormalizeTeacherOptions,
): TeacherVO {
  const r = raw as Record<string, unknown>;
  let kindergartenId =
    firstPositiveLong(raw.kindergartenId, raw.kindergarten_id, r.kindergarten_id) ?? 0;
  if (
    kindergartenId <= 0 &&
    options?.fallbackKindergartenId != null &&
    options.fallbackKindergartenId > 0
  ) {
    kindergartenId = options.fallbackKindergartenId;
  }
  const userId = firstPositiveLong(raw.userId, raw.user_id, r.user_id) ?? 0;
  const teacherId =
    firstPositiveLong(raw.teacherId, r.teacher_id, raw.id, r.teacherId) ?? 0;

  return {
    teacherId,
    kindergartenId,
    userId,
    name: nullableString(raw.name, r.name) ?? '',
    gender: nullableString(raw.gender, r.gender),
    level: nullableString(raw.level, r.level),
    startDate: nullableString(raw.startDate, r.start_date),
    endDate: nullableString(raw.endDate, r.end_date),
    status: nullableString(raw.status, r.status),
    createdAt: nullableString(raw.createdAt, r.created_at),
    updatedAt: nullableString(raw.updatedAt, r.updated_at),
  };
}

function normalizeTeacherPage(p: PageResponse<TeacherVO>): PageResponse<TeacherVO> {
  return {
    ...p,
    content: (p.content ?? []).map((row) => normalizeTeacherVO(row as TeacherApiRow)),
  };
}

/**
 * Fetch a teacher record by their user ID within the caller's active kindergarten.
 *
 * Backend: {@code GET /api/v1/teachers/by-user/{userId}}
 * Authorization: TENANT_S2_READ (KINDERGARTEN_ADMIN + TEACHER); scoped to active
 * kindergarten by the service layer.
 *
 * Returns {@code null} when no matching teacher record exists (404).
 */
export async function getTeacherByUserId(userId: number): Promise<Teacher | null> {
  try {
    const response = await apiClient.get<TeacherApiRow>(`/teachers/by-user/${userId}`);
    const vo = normalizeTeacherVO(response.data);
    return {
      teacherId: vo.teacherId > 0 ? vo.teacherId : null,
      userId: vo.userId,
      name: vo.name,
    };
  } catch (err: unknown) {
    if ((err as { response?: { status?: number } })?.response?.status === 404) {
      return null;
    }
    throw err;
  }
}

/**
 * Search the teacher roster of the caller's active kindergarten.
 *
 * Backend: {@code GET /api/v1/teachers}
 * Params: {@code keyword} (name filter), {@code userId}, standard Spring
 * {@code Pageable} ({@code page}, {@code size}, {@code sort}).
 * The {@code kindergartenId} param is accepted by this function for callers
 * that carry the context, but the backend already enforces tenant scoping via
 * the active-kindergarten session — it is NOT forwarded to the server.
 */
export async function searchTeachers(params: {
  keyword?: string;
  userId?: number;
  kindergartenId?: number;
  page?: number;
  size?: number;
  sort?: string | string[];
}): Promise<PageResponse<TeacherVO>> {
  const { keyword, userId, page = 0, size = 20, sort } = params;
  const response = await apiClient.get<PageResponse<TeacherVO>>('/teachers', {
    params: {
      page,
      size,
      ...(keyword?.trim() ? { keyword: keyword.trim() } : {}),
      ...(userId != null && userId > 0 ? { userId } : {}),
      ...(sort != null ? { sort } : {}),
    },
  });
  return normalizeTeacherPage(response.data);
}

/**
 * Fetch a single teacher by their teacher record ID within the caller's active kindergarten.
 *
 * Backend: {@code GET /api/v1/teachers/{id}}
 * Authorization: TENANT_S2_READ; the service layer enforces same-kindergarten scope.
 */
export async function getTeacher(id: number): Promise<TeacherVO> {
  const response = await apiClient.get<TeacherApiRow>(`/teachers/${id}`);
  return normalizeTeacherVO(response.data);
}

/**
 * Resolve a display name for a user who is a teacher in the caller's active kindergarten.
 *
 * Delegates to {@code getTeacherByUserId}. The {@code kindergartenId} argument is kept
 * for call-site clarity but is not forwarded to the server — the backend already scopes
 * results to the authenticated user's active kindergarten.
 *
 * Returns {@code null} when no teacher record is found for the given user.
 */
export async function fetchTeacherDisplayNameForUser(
  userId: number,
  _kindergartenId: number,
): Promise<string | null> {
  const teacher = await getTeacherByUserId(userId);
  return teacher?.name ?? null;
}
