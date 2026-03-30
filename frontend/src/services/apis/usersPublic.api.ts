import axios from 'axios';
import { apiClient } from './apiClient';
import type { PageResponse } from './appreciationLetters.api';

/** 백엔드 `UserVO` (표시용 — `loginId`·`userId` 위주) */
export type UserAccountVO = {
  userId: number;
  loginId: string;
  email: string | null;
  phone: string | null;
  status: string | null;
  lastLoginAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

function normalizeUserAccountVO(raw: unknown): UserAccountVO {
  const r = raw as Record<string, unknown>;
  /* 백엔드 MapStruct가 userId를 비우는 경우 대비: id·PK 별칭 */
  const userId = Number(r.userId ?? r.user_id ?? r.id);
  return {
    userId: Number.isFinite(userId) && userId > 0 ? userId : 0,
    loginId: String(r.loginId ?? r.login_id ?? r.loginID ?? ''),
    email: (r.email as string) ?? null,
    phone: (r.phone as string) ?? null,
    status: (r.status as string) ?? null,
    lastLoginAt: (r.lastLoginAt ?? r.last_login_at) as string | null,
    createdAt: (r.createdAt ?? r.created_at) as string | null,
    updatedAt: (r.updatedAt ?? r.updated_at) as string | null,
  };
}

/**
 * 회원 목록 페이지 (로그인 ID → user_id 해석용).
 * 백엔드 listUsers 는 keyword=null 과 동일 이슈가 있을 수 있어 `keyword: ''` 고정.
 */
export async function listUsersPage(page: number, size: number): Promise<PageResponse<UserAccountVO>> {
  const res = await apiClient.get<PageResponse<unknown>>('/users', {
    params: { page, size, keyword: '' },
  });
  const d = res.data;
  return {
    ...d,
    content: (d.content ?? []).map((row) => normalizeUserAccountVO(row)),
  };
}

/** `GET /users/{id}` — 작성자 로그인 ID 표시용 */
export async function getUserById(id: number): Promise<UserAccountVO | null> {
  try {
    const res = await apiClient.get<unknown>(`/users/${id}`);
    const row = normalizeUserAccountVO(res.data);
    /* GET 경로의 id가 곧 user_id — VO에 userId가 비어 있어도 로그인 ID 표시 가능 */
    const uid =
      row.userId > 0 ? row.userId : Number.isFinite(id) && id > 0 ? id : 0;
    return {
      ...row,
      userId: uid,
    };
  } catch (e: unknown) {
    if (axios.isAxiosError(e) && e.response?.status === 404) return null;
    throw e;
  }
}

/**
 * 상세 화면 등: 회원 PK로 로그인 ID 확보.
 * 단건 GET이 비었거나 실패해도 `/users` 페이지를 훑어 `userId` 일치 행의 `loginId`를 씀.
 */
export async function getLoginIdByUserId(userId: number): Promise<string | null> {
  if (!Number.isFinite(userId) || userId <= 0) return null;
  try {
    const direct = await getUserById(userId);
    const fromDetail = direct?.loginId?.trim();
    if (fromDetail) return fromDetail;
  } catch {
    /* 목록 폴백 */
  }
  const size = 200;
  for (let page = 0; page < 100; page++) {
    const p = await listUsersPage(page, size);
    const u = p.content?.find(
      (row) => row.userId === userId && userId > 0 && row.userId > 0,
    );
    const lid = u?.loginId?.trim();
    if (lid) return lid;
    if (p.last || !p.content?.length) break;
  }
  return null;
}
