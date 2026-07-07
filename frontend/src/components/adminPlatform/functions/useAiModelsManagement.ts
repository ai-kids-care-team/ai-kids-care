'use client';

import {
  createAiModel,
  deleteAiModel,
  getAiModels,
  updateAiModel,
  type AiModelCreatePayload,
  type AiModelUpdatePayload,
  type AiModelVO,
} from '@/services/apis/aiModels.api';
import { useCrudResource } from '@/components/adminOperations/functions/useCrudResource';

const PAGE_SIZE = 10;

/**
 * wire-orphan-management-uis (UX-01): AI 모델 목록/검색/페이지네이션 + CRUD.
 * Thin wrapper over the generic `useCrudResource` (QLT-01/D3) — same pattern as
 * `useRoomsManagement`/`useClassesManagement`, just pointed at `aiModels.api`.
 * 범위는 메타데이터 대장(name/version/status)뿐 — 학습 트리거/가중치 업로드는 비범위(design D4).
 */
export function useAiModelsManagement() {
  const resource = useCrudResource<AiModelVO, AiModelCreatePayload, AiModelUpdatePayload>({
    list: async ({ keyword, page, size }) => {
      const data = await getAiModels({ keyword, page, size });
      return { content: data.content, totalPages: data.totalPages };
    },
    create: createAiModel,
    update: updateAiModel,
    remove: deleteAiModel,
    pageSize: PAGE_SIZE,
    labels: {
      loadErrorLog: 'AI 모델 목록 조회 실패:',
      loadErrorToast: 'AI 모델 목록을 불러오지 못했습니다.',
      createSuccess: 'AI 모델을 등록했습니다.',
      createErrorToast: 'AI 모델 등록에 실패했습니다.',
      updateSuccess: 'AI 모델 정보를 수정했습니다.',
      updateErrorToast: 'AI 모델 수정에 실패했습니다.',
      deleteConfirm:
        '이 AI 모델을 완전히 삭제하시겠습니까? 과거 탐지 세션이 이 모델을 참조하고 있으면 삭제가 거부될 수 있습니다. 사용을 중단하려면 "정지" 버튼(상태 변경)을 사용하세요.',
      deleteSuccess: 'AI 모델을 삭제했습니다.',
      deleteErrorToast: 'AI 모델 삭제에 실패했습니다. 과거 탐지 세션이 이 모델을 참조 중일 수 있습니다.',
    },
  });

  return {
    items: resource.items,
    loading: resource.loading,
    error: resource.error,
    keyword: resource.keyword,
    setKeyword: resource.setKeyword,
    handleSearch: resource.handleSearch,
    page: resource.page,
    totalPages: resource.totalPages,
    setPage: resource.setPage,
    submitting: resource.submitting,
    handleCreate: resource.handleCreate,
    handleUpdate: resource.handleUpdate,
    handleDelete: resource.handleDelete,
  };
}
