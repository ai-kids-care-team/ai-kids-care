import type { AppreciationLetterVO } from '@/types/appreciationLetter';
import { apiClient } from './apiClient';

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

export const APPRECIATION_LETTERS_PAGE_SIZE = 20;

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
  const size = params?.size ?? APPRECIATION_LETTERS_PAGE_SIZE;
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
  const res = await apiClient.post<AppreciationLetterVO>('/appreciation_letters', payload);
  return res.data;
}

export async function updateAppreciationLetter(
  id: number,
  payload: AppreciationLetterWritePayload,
): Promise<AppreciationLetterVO> {
  const res = await apiClient.put<AppreciationLetterVO>(`/appreciation_letters/${id}`, payload);
  return res.data;
}

export async function deleteAppreciationLetter(id: number): Promise<void> {
  await apiClient.delete(`/appreciation_letters/${id}`);
}
