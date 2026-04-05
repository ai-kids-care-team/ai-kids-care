import { apiClient } from './apiClient';

export type CommonCode = {
  codeId: number;
  id: number;
  parentCode: string | null;
  parent_code?: string | null;
  codeGroup: string;
  code_group?: string;
  code: string;
  codeName: string;
  code_name?: string;
  sortOrder: number;
  sort_order?: number;
  isActive: boolean;
  is_active?: boolean;
  createdAt: string;
  created_at?: string;
  updatedAt: string;
  updated_at?: string;
};

export type CommonCodePage = {
  content: CommonCode[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
};

export type GetCommonCodesParams = {
  codeGroup?: string;
  code?: string;
  parentCode?: string;
  isActive?: boolean;
  page?: number;
  size?: number;
  sort?: string;
};

function normalizeCommonCode(raw: unknown): CommonCode | null {
  if (!raw || typeof raw !== 'object') return null;

  const item = raw as Record<string, unknown>;
  const codeId = Number(item.codeId ?? item.id ?? 0);
  const codeGroup = String(item.codeGroup ?? item.code_group ?? '').trim();
  const code = String(item.code ?? '').trim();
  const codeName = String(item.codeName ?? item.code_name ?? '').trim();

  if (!Number.isFinite(codeId) || codeId <= 0 || !codeGroup || !code || !codeName) {
    return null;
  }

  const parentCodeValue = item.parentCode ?? item.parent_code;
  const parentCode = parentCodeValue == null ? null : String(parentCodeValue);
  const sortOrder = Number(item.sortOrder ?? item.sort_order ?? 0);
  const isActive = Boolean(item.isActive ?? item.is_active ?? false);
  const createdAt = String(item.createdAt ?? item.created_at ?? '');
  const updatedAt = String(item.updatedAt ?? item.updated_at ?? '');

  return {
    codeId,
    id: codeId,
    parentCode,
    parent_code: parentCode,
    codeGroup,
    code_group: codeGroup,
    code,
    codeName,
    code_name: codeName,
    sortOrder,
    sort_order: sortOrder,
    isActive,
    is_active: isActive,
    createdAt,
    created_at: createdAt,
    updatedAt,
    updated_at: updatedAt,
  };
}

function normalizeCommonCodeList(raw: unknown): CommonCode[] {
  const payload =
    Array.isArray(raw)
      ? raw
      : raw && typeof raw === 'object' && Array.isArray((raw as { content?: unknown[] }).content)
        ? (raw as { content: unknown[] }).content
        : raw && typeof raw === 'object' && Array.isArray((raw as { data?: unknown[] }).data)
          ? (raw as { data: unknown[] }).data
          : [];

  return payload
    .map((item) => normalizeCommonCode(item))
    .filter((item): item is CommonCode => item !== null);
}

function normalizeCommonCodePage(raw: unknown): CommonCodePage {
  const content = normalizeCommonCodeList(raw);

  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return {
      content,
      totalElements: content.length,
      totalPages: content.length > 0 ? 1 : 0,
      size: content.length,
      number: 0,
      first: true,
      last: true,
    };
  }

  const page = raw as Record<string, unknown>;

  return {
    content,
    totalElements: Number(page.totalElements ?? content.length),
    totalPages: Number(page.totalPages ?? (content.length > 0 ? 1 : 0)),
    size: Number(page.size ?? content.length),
    number: Number(page.number ?? 0),
    first: Boolean(page.first ?? true),
    last: Boolean(page.last ?? true),
  };
}

export async function getCommonCodes(params: GetCommonCodesParams = {}): Promise<CommonCodePage> {
  const response = await apiClient.get<unknown>('/common_codes', { params });
  return normalizeCommonCodePage(response.data);
}

export async function getCommonCodeList(params: GetCommonCodesParams = {}): Promise<CommonCode[]> {
  const page = await getCommonCodes(params);
  return page.content;
}

export async function getParentCommonCodeList(codeGroup: string, parentCode: string): Promise<CommonCode[]> {
  const normalizedCodeGroup = codeGroup.trim();
  const normalizedParentCode = parentCode.trim();

  if (!normalizedCodeGroup || !normalizedParentCode) {
    throw new Error('codeGroup과 parentCode는 필수입니다.');
  }

  return getCommonCodeList({
    codeGroup: normalizedCodeGroup,
    parentCode: normalizedParentCode,
    isActive: true,
    size: 100,
    sort: 'sortOrder,asc',
  });
}
