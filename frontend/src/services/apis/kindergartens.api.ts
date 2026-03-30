import { apiClient } from './apiClient';
import type { PageResponse } from './appreciationLetters.api';

/** 백엔드 `KindergartenVO` */
export type KindergartenVO = {
  kindergartenId: number;
  name: string;
  address: string | null;
  regionCode: string | null;
  code: string | null;
  businessRegistrationNo: string | null;
  contactName: string | null;
  contactPhone: string | null;
  contactEmail: string | null;
  status: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export async function searchKindergartens(params: {
  keyword?: string;
  page?: number;
  size?: number;
  sort?: string | string[];
}): Promise<PageResponse<KindergartenVO>> {
  const page = params.page ?? 0;
  const size = params.size ?? 20;
  const keyword = params.keyword?.trim();
  const sort = params.sort;
  const res = await apiClient.get<PageResponse<KindergartenVO>>('/kindergartens', {
    params: {
      page,
      size,
      ...(keyword ? { keyword } : {}),
      ...(sort ? { sort } : {}),
    },
  });
  return res.data;
}

export async function getKindergarten(id: number): Promise<KindergartenVO> {
  const res = await apiClient.get<KindergartenVO>(`/kindergartens/${id}`);
  return res.data;
}
