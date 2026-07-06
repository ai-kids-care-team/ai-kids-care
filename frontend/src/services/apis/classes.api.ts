import { apiClient } from './apiClient';
import type { PageResponse } from './appreciationLetters.api';

/**
 * 백엔드 `ClassVO`(`GET/POST/PUT /api/v1/classes`) 와 1:1.
 * 테넌트는 세션의 `activeKindergartenId` 로 서버가 강제하므로 `kindergartenId` 는
 * 응답 표시용으로만 쓰고 요청에는 절대 실어 보내지 않는다.
 */
export type ClassVO = {
  classId: number;
  kindergartenId: number;
  name: string;
  grade: string;
  academicYear: number;
  startDate: string;
  endDate: string;
  status: string;
  createdAt: string | null;
  updatedAt: string | null;
};

/** 백엔드 `ClassCreateDTO` — kindergartenId 는 서버가 세션 컨텍스트로 채우므로 전송하지 않는다. */
export type ClassCreatePayload = {
  name: string;
  grade: string;
  academicYear: number;
  startDate: string;
  endDate: string;
  status: string;
};

/**
 * 백엔드 `ClassUpdateDTO` — PATCH 의미이나 `name`/`grade` 는 서버에서 `@NotBlank` 로
 * 여전히 필수다. 나머지(academicYear/startDate/endDate/status)만 null 허용.
 */
export type ClassUpdatePayload = {
  name: string;
  grade: string;
  academicYear?: number | null;
  startDate?: string | null;
  endDate?: string | null;
  status?: string | null;
};

export type GetClassesParams = {
  keyword?: string;
  page?: number;
  size?: number;
  sort?: string | string[];
};

/** `GET /api/v1/classes?keyword=&page=&size=` → `Page<ClassVO>` */
export async function getClasses(params?: GetClassesParams): Promise<PageResponse<ClassVO>> {
  const page = params?.page ?? 0;
  const size = params?.size ?? 20;
  const keyword = params?.keyword?.trim();
  const sort = params?.sort;
  const response = await apiClient.get<PageResponse<ClassVO>>('/classes', {
    params: {
      page,
      size,
      ...(keyword ? { keyword } : {}),
      ...(sort ? { sort } : {}),
    },
  });
  return response.data;
}

/** `GET /api/v1/classes/{id}` — 404 는 그대로 throw(호출부에서 "찾을 수 없음"으로 표시) */
export async function getClass(id: number): Promise<ClassVO> {
  const response = await apiClient.get<ClassVO>(`/classes/${id}`);
  return response.data;
}

/** `POST /api/v1/classes` */
export async function createClass(payload: ClassCreatePayload): Promise<ClassVO> {
  const response = await apiClient.post<ClassVO>('/classes', payload);
  return response.data;
}

/** `PUT /api/v1/classes/{id}` */
export async function updateClass(id: number, payload: ClassUpdatePayload): Promise<ClassVO> {
  const response = await apiClient.put<ClassVO>(`/classes/${id}`, payload);
  return response.data;
}

/** `DELETE /api/v1/classes/{id}` — 204 No Content */
export async function deleteClass(id: number): Promise<void> {
  await apiClient.delete(`/classes/${id}`);
}
