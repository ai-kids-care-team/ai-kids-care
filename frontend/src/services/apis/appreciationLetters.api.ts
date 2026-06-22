import { apiClient } from './apiClient';
import type { AppreciationLetterVO } from '@/types/appreciationLetter';

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

/** 백엔드 `AppreciationLetterCreateDTO` */
export type AppreciationLetterCreatePayload = {
  targetType: string;
  targetId: number;
  title: string;
  content: string;
  isPublic: boolean;
};

/** 백엔드 `AppreciationLetterUpdateDTO` */
export type AppreciationLetterUpdatePayload = {
  title: string;
  content: string;
  isPublic: boolean;
};

/** 프론트 UI에서 한 페이지에 보여줄 개수 */
export const APPRECIATION_LETTERS_PAGE_SIZE = 6;

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
  const response = await apiClient.get<PageResponse<AppreciationLetterVO>>(
    '/appreciation_letters',
    {
      params: {
        page,
        size,
        ...(keyword ? { keyword } : {}),
        ...(sort ? { sort } : {}),
      },
    },
  );
  return response.data;
}

export async function getAppreciationLetterDetail(id: number): Promise<AppreciationLetterVO> {
  const response = await apiClient.get<AppreciationLetterVO>(`/appreciation_letters/${id}`);
  return response.data;
}

export async function createAppreciationLetter(
  payload: AppreciationLetterCreatePayload,
): Promise<AppreciationLetterVO> {
  const response = await apiClient.post<AppreciationLetterVO>('/appreciation_letters', payload);
  return response.data;
}

export async function updateAppreciationLetter(
  id: number,
  payload: AppreciationLetterUpdatePayload,
): Promise<AppreciationLetterVO> {
  const response = await apiClient.put<AppreciationLetterVO>(
    `/appreciation_letters/${id}`,
    payload,
  );
  return response.data;
}

export async function deleteAppreciationLetter(id: number): Promise<void> {
  await apiClient.delete(`/appreciation_letters/${id}`);
}
