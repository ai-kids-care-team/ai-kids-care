import { apiClient } from './apiClient';
import type { PageResponse } from './appreciationLetters.api';

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

type TeacherApiRow = TeacherVO & {
  id?: number;
  kindergarten_id?: number;
  user_id?: number;
  teacher_id?: number;
};

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

/** 목록·상세 공통: camelCase / snake_case / 레거시 `id` 보정 */
export function normalizeTeacherVO(raw: TeacherApiRow): TeacherVO {
  const r = raw as Record<string, unknown>;
  const teacherId =
    firstPositiveLong(
      raw.teacherId,
      raw.id,
      r.teacher_id,
      r.teacherId,
    ) ?? 0;
  const kindergartenId =
    firstPositiveLong(
      raw.kindergartenId,
      raw.kindergarten_id,
      r.kindergarten_id,
    ) ?? 0;
  const userId =
    firstPositiveLong(
      raw.userId,
      raw.user_id,
      r.user_id,
    ) ?? 0;
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
  const keyword = params.keyword?.trim();
  const sort = params.sort;
  const kgId = params.kindergartenId;
  const res = await apiClient.get<PageResponse<TeacherVO>>('/teachers', {
    params: {
      page,
      size,
      ...(keyword ? { keyword } : {}),
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
