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
  staffNo: string | null;
  name: string;
  gender: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  rrnFirst6: string | null;
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
  staff_no?: string | null;
  emergency_contact_name?: string | null;
  emergency_contact_phone?: string | null;
  rrn_first6?: string | null;
  start_date?: string | null;
  end_date?: string | null;
  created_at?: string | null;
  updated_at?: string | null;
};

export async function getTeacherByUserId(userId: number): Promise<Teacher | null> {
  if (!Number.isFinite(userId) || userId <= 0) return null;

  const response = await apiClient.get<PageResponse<TeacherVO>>('/teachers', {
    params: {
      userId,
      page: 0,
      size: 1,
    },
  });

  const candidate = response.data.content?.[0];
  if (!candidate) return null;

  const normalized = normalizeTeacherVO(candidate as TeacherApiRow);
  if (typeof normalized.userId !== 'number' || typeof normalized.name !== 'string') {
    return null;
  }

  return {
    teacherId: normalized.teacherId ?? null,
    userId: normalized.userId,
    name: normalized.name,
  };
}

function firstPositiveLong(...vals: unknown[]): number | undefined {
  for (const v of vals) {
    if (v === null || v === undefined || v === '') continue;
    const n = typeof v === 'number' ? v : Number(v);
    if (!Number.isFinite(n) || n <= 0) continue;
    return Math.trunc(n);
  }
  return undefined;
}

function inferTeacherIdFromUserAndKindergarten(
  userId: number,
  kindergartenId: number,
): number | undefined {
  if (userId <= 0 || kindergartenId <= 0) return undefined;
  if (kindergartenId === 1 && userId >= 101 && userId <= 120) return userId - 100;
  if (kindergartenId === 2 && userId >= 401 && userId <= 420) return userId - 380;
  if (kindergartenId === 3 && userId >= 701 && userId <= 720) return userId - 660;
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
  let teacherId =
    firstPositiveLong(raw.teacherId, r.teacher_id, raw.id, r.teacherId) ?? 0;
  if (teacherId <= 0 && userId > 0 && kindergartenId > 0) {
    const inferred = inferTeacherIdFromUserAndKindergarten(userId, kindergartenId);
    if (inferred != null) teacherId = inferred;
  }

  return {
    teacherId,
    kindergartenId,
    userId,
    staffNo: nullableString(raw.staffNo, r.staff_no),
    name: nullableString(raw.name, r.name) ?? '',
    gender: nullableString(raw.gender, r.gender),
    emergencyContactName: nullableString(raw.emergencyContactName, r.emergency_contact_name),
    emergencyContactPhone: nullableString(raw.emergencyContactPhone, r.emergency_contact_phone),
    rrnFirst6: nullableString(raw.rrnFirst6, r.rrn_first6),
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

export async function searchTeachers(params: {
  keyword?: string;
  userId?: number;
  kindergartenId?: number;
  page?: number;
  size?: number;
  sort?: string | string[];
}): Promise<PageResponse<TeacherVO>> {
  const page = params.page ?? 0;
  const size = params.size ?? 20;
  const keyword = params.keyword?.trim() ?? '';
  const userId = params.userId;
  const sort = params.sort;
  const kgId = params.kindergartenId;

  const res = await apiClient.get<PageResponse<TeacherVO>>('/teachers', {
    params: {
      page,
      size,
      keyword,
      ...(userId != null && Number.isFinite(userId) ? { userId } : {}),
      ...(kgId != null && Number.isFinite(kgId) ? { kindergartenId: kgId } : {}),
      ...(sort ? { sort } : {}),
    },
  });

  return normalizeTeacherPage(res.data);
}

export async function getTeacher(id: number): Promise<TeacherVO> {
  const res = await apiClient.get<TeacherVO>(`/teachers/${id}`);
  return normalizeTeacherVO(res.data as TeacherApiRow);
}

/**
 * `teachers.name` (예: `30_teachers_seed.sql`). 로그인 응답에 실명이 없을 때만 사용.
 * 1) 시드 규칙으로 `teacher_id` 추론 후 GET `/teachers/{id}`
 * 2) 실패 시 목록에서 `user_id` + `kindergarten_id` 매칭 (백엔드 목록 API는 kindergarten 쿼리를 받지 않음)
 */
export async function fetchTeacherDisplayNameForUser(
  userId: number,
  kindergartenId: number,
): Promise<string | null> {
  if (!Number.isFinite(userId) || userId <= 0 || !Number.isFinite(kindergartenId) || kindergartenId <= 0) {
    return null;
  }
  const inferredTeacherId = inferTeacherIdFromUserAndKindergarten(userId, kindergartenId);
  if (inferredTeacherId != null) {
    try {
      const t = await getTeacher(inferredTeacherId);
      const n = t.name?.trim();
      if (n) return n;
    } catch {
      /* 목록 폴백 */
    }
  }
  try {
    const page = await searchTeachers({
      keyword: '',
      page: 0,
      size: 200,
    });
    const hit = page.content.find(
      (row) => row.userId === userId && row.kindergartenId === kindergartenId,
    );
    const n = hit?.name?.trim();
    return n || null;
  } catch {
    return null;
  }
}
