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
  rrnEncrypted: string | null;
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
    ...raw,
    teacherId,
    kindergartenId,
    userId,
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
