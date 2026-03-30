import axios from 'axios';
import { apiClient } from './apiClient';
import type { PageResponse } from './appreciationLetters.api';
import { listUsersPage } from './usersPublic.api';

/** 백엔드 `GuardianVO` */
export type GuardianVO = {
  guardianId: number;
  kindergartenId: number;
  userId: number;
  name: string;
  rrnEncrypted: string | null;
  rrnFirst6: string | null;
  gender: string | null;
  address: string | null;
  status: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

function normalizeGuardianVO(raw: unknown): GuardianVO {
  const r = raw as Record<string, unknown>;
  const guardianId = Number(r.guardianId ?? r.guardian_id);
  const kindergartenId = Number(r.kindergartenId ?? r.kindergarten_id);
  const userId = Number(r.userId ?? r.user_id);
  return {
    guardianId: Number.isFinite(guardianId) && guardianId > 0 ? guardianId : 0,
    kindergartenId: Number.isFinite(kindergartenId) && kindergartenId > 0 ? kindergartenId : 0,
    userId: Number.isFinite(userId) && userId > 0 ? userId : 0,
    name: String(r.name ?? '').trim(),
    rrnEncrypted: (r.rrnEncrypted ?? r.rrn_encrypted) as string | null,
    rrnFirst6: (r.rrnFirst6 ?? r.rrn_first6) as string | null,
    gender: (r.gender as string) ?? null,
    address: (r.address as string) ?? null,
    status: (r.status as string) ?? null,
    createdAt: (r.createdAt ?? r.created_at) as string | null,
    updatedAt: (r.updatedAt ?? r.updated_at) as string | null,
  };
}

async function fetchGuardiansPage(page: number, size: number): Promise<PageResponse<GuardianVO>> {
  const res = await apiClient.get<PageResponse<unknown>>('/guardians', {
    params: { page, size },
  });
  const d = res.data;
  return {
    ...d,
    content: (d.content ?? []).map((row) => normalizeGuardianVO(row)),
  };
}

/**
 * `GET /guardians/by-user/...` 없이 목록을 페이지로 훑어 `user_id` 일치 행을 찾습니다.
 */
export async function getGuardianByUserId(userId: number): Promise<GuardianVO | null> {
  if (!Number.isFinite(userId) || userId <= 0) return null;
  const size = 200;
  let page = 0;
  for (;;) {
    const p = await fetchGuardiansPage(page, size);
    const hit = p.content?.find((g) => g.userId === userId);
    if (hit) return hit;
    if (p.last || !p.content?.length) break;
    page++;
  }
  return null;
}

/**
 * 로그인 ID → users 목록에서 user_id → guardians 목록에서 이름.
 * (`/guardians/by-login-id` 미구현 대응)
 */
export async function getGuardianByLoginId(loginId: string): Promise<GuardianVO | null> {
  const key = loginId.trim().toLowerCase();
  if (!key) return null;
  const size = 500;
  for (let page = 0; page < 50; page++) {
    const p = await listUsersPage(page, size);
    const u = p.content?.find((row) => row.loginId?.toLowerCase() === key);
    if (u && u.userId > 0) return getGuardianByUserId(u.userId);
    if (p.last || !p.content?.length) break;
  }
  return null;
}

/**
 * `guardians.name` 조회: 먼저 회원 ID로 목록 스캔, 없으면 로그인 ID로 users→보호자.
 */
export async function resolveGuardianNameFromUserKeys(opts: {
  userId?: number | null;
  loginId?: string | null;
}): Promise<string | null> {
  const uid = opts.userId;
  const login = (opts.loginId || '').trim();
  if (uid != null && Number.isFinite(uid) && uid > 0) {
    const g = await getGuardianByUserId(uid);
    if (g?.name) return g.name.trim();
  }
  if (login) {
    const g2 = await getGuardianByLoginId(login);
    if (g2?.name) return g2.name.trim();
  }
  return null;
}
