import axios from 'axios';
import { apiClient } from './apiClient';

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

/**
 * 회원 ID(`user_id`)로 보호자 프로필 조회 (`GuardianController` GET `/guardians/by-user/{userId}`).
 * 보호자가 아니면 404 → null.
 */
export async function getGuardianByUserId(userId: number): Promise<GuardianVO | null> {
  try {
    const res = await apiClient.get<GuardianVO>(`/guardians/by-user/${userId}`);
    return res.data;
  } catch (e: unknown) {
    if (axios.isAxiosError(e) && e.response?.status === 404) return null;
    throw e;
  }
}

/** `users.login_id` → `GuardianController` GET `/guardians/by-login-id/{loginId}` */
export async function getGuardianByLoginId(loginId: string): Promise<GuardianVO | null> {
  const key = loginId.trim();
  if (!key) return null;
  try {
    const res = await apiClient.get<GuardianVO>(
      `/guardians/by-login-id/${encodeURIComponent(key)}`,
    );
    return res.data;
  } catch (e: unknown) {
    if (axios.isAxiosError(e) && e.response?.status === 404) return null;
    throw e;
  }
}

/**
 * `guardians.name` 조회: 먼저 회원 ID(`by-user`), 없으면 로그인 ID(`by-login-id`).
 * 시드 `guardians_seed`의 name과 동일한 값.
 */
export async function resolveGuardianNameFromUserKeys(opts: {
  userId?: number | null;
  loginId?: string | null;
}): Promise<string | null> {
  const uid = opts.userId;
  const login = (opts.loginId || '').trim();
  if (uid != null && Number.isFinite(uid) && uid > 0) {
    const g = await getGuardianByUserId(uid);
    if (g?.name?.trim()) return g.name.trim();
  }
  if (login) {
    const g2 = await getGuardianByLoginId(login);
    if (g2?.name?.trim()) return g2.name.trim();
  }
  return null;
}
