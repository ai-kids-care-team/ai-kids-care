'use client';

import {
  createClass,
  deleteClass,
  getClasses,
  updateClass,
  type ClassCreatePayload,
  type ClassUpdatePayload,
  type ClassVO,
} from '@/services/apis/classes.api';
import { useCrudResource } from './useCrudResource';

const PAGE_SIZE = 10;

/**
 * director-operations-ui (C6/UX-05): 학급(classes) 목록/검색/페이지네이션 + CRUD 오케스트레이션.
 *
 * refactor-cross-cutting-debt (QLT-01 / D3): thin wrapper over the generic `useCrudResource` —
 * outward return shape is unchanged from before the refactor, so consuming components
 * (`ClassesSection`) need zero edits.
 */
export function useClassesManagement() {
  const resource = useCrudResource<ClassVO, ClassCreatePayload, ClassUpdatePayload>({
    list: async ({ keyword, page, size }) => {
      const data = await getClasses({ keyword, page, size });
      return { content: data.content, totalPages: data.totalPages };
    },
    create: createClass,
    update: updateClass,
    remove: deleteClass,
    pageSize: PAGE_SIZE,
    labels: {
      loadErrorLog: '학급 목록 조회 실패:',
      loadErrorToast: '학급 목록을 불러오지 못했습니다.',
      createSuccess: '학급을 추가했습니다.',
      createErrorToast: '학급 추가에 실패했습니다.',
      updateSuccess: '학급 정보를 수정했습니다.',
      updateErrorToast: '학급 수정에 실패했습니다.',
      deleteConfirm: '정말 이 학급을 삭제하시겠습니까?',
      deleteSuccess: '학급을 삭제했습니다.',
      deleteErrorToast: '학급 삭제에 실패했습니다.',
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
