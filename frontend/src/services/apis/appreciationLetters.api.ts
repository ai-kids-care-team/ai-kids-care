import axios from 'axios';
import type { AppreciationLetterVO } from '@/types/appreciationLetter';
import { API_BASE_URL } from '@/config/api';
import { apiClient } from './apiClient';

function isAxios401(err: unknown): boolean {
  return axios.isAxiosError(err) && err.response?.status === 401;
}

/**
 * 만료·불일치 Bearer로 401이 나도, 백엔드가 해당 경로를 공개(permitAll)인 경우 무인증 재시도로 성공할 수 있음.
 */
async function appreciationMutationWith401RetryWithoutAuth<T>(
  primary: () => Promise<T>,
  fallback: () => Promise<T>,
): Promise<T> {
  try {
    return await primary();
  } catch (e) {
    if (isAxios401(e)) {
      return fallback();
    }
    throw e;
  }
}

/** Spring Data `Page` JSON */
export type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
};

/** 백엔드 `AppreciationLetterCreateDTO` / `AppreciationLetterUpdateDTO` 필드 정렬 */
export type AppreciationLetterWritePayload = {
  kindergartenId: number;
  senderUserId: number;
  targetType: string;
  targetId: number;
  title: string;
  content: string;
  isPublic: boolean;
  status: string;
};

/** 프론트 UI에서 한 페이지에 보여줄 개수 */
export const APPRECIATION_LETTERS_PAGE_SIZE = 6;

/** 목록 조회 시 한 번에 가져올 최대 개수(프론트에서 다시 페이지 나눔) */
export const APPRECIATION_LETTERS_FETCH_LIMIT = 200;

export type GetAppreciationLettersParams = {
  keyword?: string;
  page?: number;
  size?: number;
  sort?: string | string[];
};

export async function getAppreciationLetters(
  params?: GetAppreciationLettersParams,
): Promise<PageResponse<AppreciationLetterVO>> {
  const page = params?.page ?? 0;
  const size = params?.size ?? APPRECIATION_LETTERS_FETCH_LIMIT;
  const keyword = params?.keyword?.trim();
  const sort = params?.sort;
  const response = await apiClient.get<PageResponse<AppreciationLetterVO>>('/appreciation_letters', {
    params: {
      page,
      size,
      ...(keyword ? { keyword } : {}),
      ...(sort ? { sort } : {}),
    },
  });
  return response.data;
}

const detailInFlight = new Map<number, Promise<AppreciationLetterVO>>();

export async function getAppreciationLetterDetail(id: number): Promise<AppreciationLetterVO> {
  const existing = detailInFlight.get(id);
  if (existing) return existing;

  const request = apiClient
    .get<AppreciationLetterVO>(`/appreciation_letters/${id}`)
    .then((res) => res.data)
    .finally(() => {
      detailInFlight.delete(id);
    });

  detailInFlight.set(id, request);
  return request;
}

export async function createAppreciationLetter(
  payload: AppreciationLetterWritePayload,
): Promise<AppreciationLetterVO> {
  return appreciationMutationWith401RetryWithoutAuth(
    () => apiClient.post<AppreciationLetterVO>('/appreciation_letters', payload).then((r) => r.data),
    () =>
      axios
        .post<AppreciationLetterVO>(`${API_BASE_URL}/appreciation_letters`, payload, {
          headers: { 'Content-Type': 'application/json' },
        })
        .then((r) => r.data),
  );
}

export async function updateAppreciationLetter(
  id: number,
  payload: AppreciationLetterWritePayload,
): Promise<AppreciationLetterVO> {
  return appreciationMutationWith401RetryWithoutAuth(
    () =>
      apiClient.put<AppreciationLetterVO>(`/appreciation_letters/${id}`, payload).then((r) => r.data),
    () =>
      axios
        .put<AppreciationLetterVO>(`${API_BASE_URL}/appreciation_letters/${id}`, payload, {
          headers: { 'Content-Type': 'application/json' },
        })
        .then((r) => r.data),
  );
}

export async function deleteAppreciationLetter(id: number): Promise<void> {
  await appreciationMutationWith401RetryWithoutAuth(
    () => apiClient.delete(`/appreciation_letters/${id}`).then(() => undefined),
    () => axios.delete(`${API_BASE_URL}/appreciation_letters/${id}`).then(() => undefined),
  );
}
