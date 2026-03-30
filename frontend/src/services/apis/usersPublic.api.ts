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
  const userId = Number(r.userId ?? r.user_id);
  return {
    userId: Number.isFinite(userId) && userId > 0 ? userId : 0,
    loginId: String(r.loginId ?? r.login_id ?? ''),
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
    return row.userId > 0 ? row : null;
  } catch (e: unknown) {
    if (axios.isAxiosError(e) && e.response?.status === 404) return null;
    throw e;
  }
}
