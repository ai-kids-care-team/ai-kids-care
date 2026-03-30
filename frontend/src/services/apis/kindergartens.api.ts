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

function normalizeKindergartenVO(raw: unknown): KindergartenVO {
  const r = raw as Record<string, unknown>;
  const id = Number(r.kindergartenId ?? r.kindergarten_id);
  const name = String(r.name ?? '').trim();
  return {
    kindergartenId: Number.isFinite(id) && id > 0 ? id : 0,
    name: name.length > 0 ? name : '—',
    address: (r.address as string) ?? null,
    regionCode: (r.regionCode ?? r.region_code) as string | null,
    code: (r.code as string) ?? null,
    businessRegistrationNo: (r.businessRegistrationNo ?? r.business_registration_no) as string | null,
    contactName: (r.contactName ?? r.contact_name) as string | null,
    contactPhone: (r.contactPhone ?? r.contact_phone) as string | null,
    contactEmail: (r.contactEmail ?? r.contact_email) as string | null,
    status: (r.status as string) ?? null,
    createdAt: (r.createdAt ?? r.created_at) as string | null,
    updatedAt: (r.updatedAt ?? r.updated_at) as string | null,
  };
}

function normalizeKindergartenPage(p: PageResponse<unknown>): PageResponse<KindergartenVO> {
  return {
    ...p,
    content: (p.content ?? []).map((row) => normalizeKindergartenVO(row)),
  };
}

/**
 * 백엔드 `findByNameContains`는 keyword 가 null 이면 결과가 비는 경우가 있어,
 * 항상 `keyword` 쿼리를 보냅니다(빈 문자열 → 이름에 "" 포함 = 전체에 가깝게 조회).
 */
export async function searchKindergartens(params: {
  keyword?: string;
  page?: number;
  size?: number;
  sort?: string | string[];
}): Promise<PageResponse<KindergartenVO>> {
  const page = params.page ?? 0;
  const size = params.size ?? 20;
  const keyword = params.keyword?.trim() ?? '';
  const sort = params.sort;
  const res = await apiClient.get<PageResponse<unknown>>('/kindergartens', {
    params: {
      page,
      size,
      keyword,
      ...(sort ? { sort } : {}),
    },
  });
  return normalizeKindergartenPage(res.data);
}

export async function getKindergarten(id: number): Promise<KindergartenVO> {
  const res = await apiClient.get<unknown>(`/kindergartens/${id}`);
  return normalizeKindergartenVO(res.data);
}
