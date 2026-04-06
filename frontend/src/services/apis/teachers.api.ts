import { apiClient } from './apiClient';
import type { PageResponse } from './appreciationLetters.api';

export type Teacher = {
  teacherId: number | null;
  userId: number;
  name: string;
};

/** 백엔드 `TeacherVO` (레거시/버그 응답은 `id`만 오는 경우 있음) */
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

/**
 * GET /teachers/by-user/{userId}
 * 백엔드에서 추가한 getTeacherNameByUserId 엔드포인트 사용
 */
export async function getTeacherByUserId(userId: number): Promise<Teacher | null> {
  if (!Number.isFinite(userId) || userId <= 0) return null;

  const response = await apiClient.get<Teacher | { data?: Teacher }>(`/teachers/by-user/${userId}`);

  const payload: any = response.data;
  if (!payload) return null;

  const candidate = payload.data ?? payload;

  if (typeof candidate.userId === 'number' && typeof candidate.name === 'string') {
    return {
      teacherId: candidate.teacherId ?? null,
      userId: candidate.userId,
      name: candidate.name,
    };
  }
  return null;
}


/** null/빈값은 건너뜀 — `Number(null)`이 0이 되어 전 행이 교사 ID 0으로 보이는 버그 방지 */
function firstPositiveLong(...vals: unknown[]): number | undefined {
  for (const v of vals) {
    if (v === null || v === undefined || v === '') continue;
    const n = typeof v === 'number' ? v : Number(v);
    if (!Number.isFinite(n) || n <= 0) continue;
    return Math.trunc(n);
  }
  return undefined;
}

/**
 * 백엔드 `TeacherMapper`가 `teacherId`를 채우지 않는 경우(항상 null) 대비.
 * 시드 기준: 원1 교사 user 101–120 → teacher_id 1–20, 원2 401–420 → 21–40, 원3 701–720 → 41–60.
 */
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

/** 목록·상세 공통: camelCase / snake_case / 레거시 `id` 보정 + teacherId 누락 시 추론 */
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
  /** 지정 시 해당 유치원 소속 교사만 */
  kindergartenId?: number;
  page?: number;
  size?: number;
  sort?: string | string[];
}): Promise<PageResponse<TeacherVO>> {
  const page = params.page ?? 0;
  const size = params.size ?? 20;
  /* 백엔드 `findByNameContains`는 keyword=null 일 때 목록이 비는 경우가 있어 항상 전송 */
  const keyword = params.keyword?.trim() ?? '';
  const sort = params.sort;
  const kgId = params.kindergartenId;
  const res = await apiClient.get<PageResponse<TeacherVO>>('/teachers', {
    params: {
      page,
      size,
      keyword,
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
