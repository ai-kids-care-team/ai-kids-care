'use client';

import {
  createRoom,
  deleteRoom,
  getRooms,
  updateRoom,
  type RoomCreatePayload,
  type RoomUpdatePayload,
  type RoomVO,
} from '@/services/apis/rooms.api';
import { useCrudResource } from './useCrudResource';

const PAGE_SIZE = 10;

/**
 * director-operations-ui (C6/UX-05): 교실(rooms) 목록/검색/페이지네이션 + CRUD — classes 와 대칭.
 *
 * refactor-cross-cutting-debt (QLT-01 / D3): thin wrapper over the generic `useCrudResource` —
 * outward return shape is unchanged from before the refactor, so consuming components
 * (`RoomsSection`) need zero edits.
 */
export function useRoomsManagement() {
  const resource = useCrudResource<RoomVO, RoomCreatePayload, RoomUpdatePayload>({
    list: async ({ keyword, page, size }) => {
      const data = await getRooms({ keyword, page, size });
      return { content: data.content, totalPages: data.totalPages };
    },
    create: createRoom,
    update: updateRoom,
    remove: deleteRoom,
    pageSize: PAGE_SIZE,
    labels: {
      loadErrorLog: '교실 목록 조회 실패:',
      loadErrorToast: '교실 목록을 불러오지 못했습니다.',
      createSuccess: '교실을 추가했습니다.',
      createErrorToast: '교실 추가에 실패했습니다.',
      updateSuccess: '교실 정보를 수정했습니다.',
      updateErrorToast: '교실 수정에 실패했습니다.',
      deleteConfirm: '정말 이 교실을 삭제하시겠습니까?',
      deleteSuccess: '교실을 삭제했습니다.',
      deleteErrorToast: '교실 삭제에 실패했습니다.',
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
