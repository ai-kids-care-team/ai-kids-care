import { apiClient } from './apiClient';

export type Teacher = {
  teacherId: number | null;
  userId: number;
  name: string;
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


