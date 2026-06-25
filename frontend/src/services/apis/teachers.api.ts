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

export async function getTeacherByUserId(userId: number): Promise<Teacher | null> {
  void userId;
  console.warn('[stub] getTeacherByUserId: not yet wired — teacher authorization lane B5 pending');
  return null;
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

export async function searchTeachers(params: {
  keyword?: string;
  userId?: number;
  kindergartenId?: number;
  page?: number;
  size?: number;
  sort?: string | string[];
}): Promise<PageResponse<TeacherVO>> {
  void params;
  console.warn('[stub] searchTeachers: not yet wired — teacher authorization lane B5 pending');
  return normalizeTeacherPage({
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 0,
    number: 0,
    first: true,
    last: true,
  });
}

export async function getTeacher(id: number): Promise<TeacherVO> {
  void id;
  console.warn('[stub] getTeacher: not yet wired — teacher authorization lane B5 pending');
  throw new Error('Teacher profile reads are unavailable until tenant authorization exists');
}

export async function fetchTeacherDisplayNameForUser(
  userId: number,
  kindergartenId: number,
): Promise<string | null> {
  void userId;
  void kindergartenId;
  console.warn('[stub] fetchTeacherDisplayNameForUser: not yet wired — teacher authorization lane B5 pending');
  return null;
}
