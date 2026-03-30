import axios from 'axios';
import { apiClient } from './apiClient';

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

/** `GET /users/{id}` — 작성자 로그인 ID 표시용 */
export async function getUserById(id: number): Promise<UserAccountVO | null> {
  try {
    const res = await apiClient.get<UserAccountVO>(`/users/${id}`);
    return res.data;
  } catch (e: unknown) {
    if (axios.isAxiosError(e) && e.response?.status === 404) return null;
    throw e;
  }
}
