import type { AppreciationLetterVO } from '@/types/appreciationLetter';

function appreciationLettersUnavailable(): never {
  throw new Error(
    'Appreciation letters are unavailable until an authenticated, tenant-scoped API is implemented.',
  );
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
  void params;
  return appreciationLettersUnavailable();
}

export async function getAppreciationLetterDetail(id: number): Promise<AppreciationLetterVO> {
  void id;
  return appreciationLettersUnavailable();
}

export async function createAppreciationLetter(
  payload: AppreciationLetterWritePayload,
): Promise<AppreciationLetterVO> {
  void payload;
  return appreciationLettersUnavailable();
}

export async function updateAppreciationLetter(
  id: number,
  payload: AppreciationLetterWritePayload,
): Promise<AppreciationLetterVO> {
  void id;
  void payload;
  return appreciationLettersUnavailable();
}

export async function deleteAppreciationLetter(id: number): Promise<void> {
  void id;
  return appreciationLettersUnavailable();
}
