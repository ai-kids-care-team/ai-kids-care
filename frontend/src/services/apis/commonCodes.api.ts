import { apiClient } from './apiClient';

export type CommonCode = {
  id: number;
  parentCode?: string | null;
  parent_code?: string | null;
  codeGroup: string;
  code_group?: string;
  code: string;
  codeName?: string;
  code_name?: string;
  sortOrder: number;
  sort_order?: number;
  isActive: boolean;
  is_active?: boolean;
  createdAt: string; // ISO DateTime (OffsetDateTime)
  created_at?: string;
  updatedAt: string; // ISO DateTime
  updated_at?: string;
};

/**
 * GET /common_codes/code_group/{codeGroup}/parent_code/{parentCode}
 * 예) http://localhost:8081/api/v1/common_codes/code_group/detection_events/parent_code/event_type
 */
export async function getParentCommonCodeList(
  codeGroup: string,
  parentCode: string,
): Promise<CommonCode[]> {
  const normalizedCodeGroup = codeGroup.trim();
  const normalizedParentCode = parentCode.trim();

  if (!normalizedCodeGroup || !normalizedParentCode) {
    throw new Error('codeGroup과 parentCode는 필수입니다.');
  }

  const response = await apiClient.get<CommonCode[] | { content?: CommonCode[]; data?: CommonCode[] }>(
    `/common_codes/code_group/${encodeURIComponent(normalizedCodeGroup)}/parent_code/${encodeURIComponent(
      normalizedParentCode,
    )}`,
  );
  const payload = response.data;
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.content)) return payload.content;
  if (Array.isArray(payload?.data)) return payload.data;
  return [];
}

