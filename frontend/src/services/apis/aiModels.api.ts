import { apiClient } from './apiClient';
import type { PageResponse } from './appreciationLetters.api';

/**
 * wire-orphan-management-uis (UX-01): 백엔드 `AiModelVO`(`GET/POST/PUT/DELETE /api/v1/ai_models`)
 * 와 1:1. `AiModelController`/`AiModelService`는 `PLATFORM_METADATA_READ`(PLATFORM_IT_ADMIN +
 * SUPERADMIN)/`PLATFORM_METADATA_WRITE`(PLATFORM_IT_ADMIN 전용) 게이트를 건다 — 플랫폼 스코프라
 * `kindergartenId` 개념 자체가 없다(요청에 싣지 않음, rooms/classes 와 동일 원칙).
 */
export type AiModelVO = {
  modelId: number;
  name: string;
  version: string;
  status: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

/** 백엔드 `AiModelCreateDTO` — `name`/`version`/`status` 모두 `@NotBlank`/`@NotNull` 필수. */
export type AiModelCreatePayload = {
  name: string;
  version: string;
  status: string;
};

/** 백엔드 `AiModelUpdateDTO` — `name`/`version` 은 update 에서도 여전히 `@NotBlank` 필수. */
export type AiModelUpdatePayload = {
  name: string;
  version: string;
  status?: string | null;
};

export type GetAiModelsParams = {
  keyword?: string;
  page?: number;
  size?: number;
  sort?: string | string[];
};

/** `GET /api/v1/ai_models?keyword=&page=&size=` → `Page<AiModelVO>` */
export async function getAiModels(params?: GetAiModelsParams): Promise<PageResponse<AiModelVO>> {
  const page = params?.page ?? 0;
  const size = params?.size ?? 20;
  const keyword = params?.keyword?.trim();
  const sort = params?.sort;
  const response = await apiClient.get<PageResponse<AiModelVO>>('/ai_models', {
    params: {
      page,
      size,
      ...(keyword ? { keyword } : {}),
      ...(sort ? { sort } : {}),
    },
  });
  return response.data;
}

/** `GET /api/v1/ai_models/{id}` */
export async function getAiModel(id: number): Promise<AiModelVO> {
  const response = await apiClient.get<AiModelVO>(`/ai_models/${id}`);
  return response.data;
}

/** `POST /api/v1/ai_models` */
export async function createAiModel(payload: AiModelCreatePayload): Promise<AiModelVO> {
  const response = await apiClient.post<AiModelVO>('/ai_models', payload);
  return response.data;
}

/** `PUT /api/v1/ai_models/{id}` */
export async function updateAiModel(id: number, payload: AiModelUpdatePayload): Promise<AiModelVO> {
  const response = await apiClient.put<AiModelVO>(`/ai_models/${id}`, payload);
  return response.data;
}

/**
 * `DELETE /api/v1/ai_models/{id}` — 204 No Content. 하드 삭제(레코드 자체 제거).
 * D4: `detection_sessions.model_id` 가 `nullable=false`/`optional=false` FK 로 이 모델을
 * 참조할 수 있어, 과거에 한 번이라도 쓰인 모델을 지우면 DB FK 제약 위반(500)으로 실패할 수
 * 있다. "정지"(비활성화) 용도로는 `updateAiModel`로 `status`를 DISABLED 로 바꾸는 편이 안전
 * 하다 — 이 함수는 잘못 등록된 레코드를 완전히 지우는 별도 액션 전용.
 */
export async function deleteAiModel(id: number): Promise<void> {
  await apiClient.delete(`/ai_models/${id}`);
}
