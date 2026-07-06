'use client';

import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
import {
  createRoom,
  deleteRoom,
  getRooms,
  updateRoom,
  type RoomCreatePayload,
  type RoomUpdatePayload,
  type RoomVO,
} from '@/services/apis/rooms.api';
import { getApiErrorMessage } from '@/components/letters/api-error-message';

const PAGE_SIZE = 10;

/** director-operations-ui (C6/UX-05): 교실(rooms) 목록/검색/페이지네이션 + CRUD — classes 와 대칭. */
export function useRoomsManagement() {
  const [items, setItems] = useState<RoomVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getRooms({ keyword: appliedKeyword || undefined, page, size: PAGE_SIZE });
      setItems(data.content);
      setTotalPages(data.totalPages);
    } catch (e) {
      console.warn('교실 목록 조회 실패:', e);
      setError(getApiErrorMessage(e, '교실 목록을 불러오지 못했습니다.'));
    } finally {
      setLoading(false);
    }
  }, [appliedKeyword, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleSearch = useCallback(() => {
    setAppliedKeyword(keyword.trim());
    setPage(0);
  }, [keyword]);

  const handleCreate = useCallback(
    async (payload: RoomCreatePayload) => {
      setSubmitting(true);
      try {
        await createRoom(payload);
        toast.success('교실을 추가했습니다.');
        setPage(0);
        await load();
        return true;
      } catch (e) {
        toast.error(getApiErrorMessage(e, '교실 추가에 실패했습니다.'));
        return false;
      } finally {
        setSubmitting(false);
      }
    },
    [load],
  );

  const handleUpdate = useCallback(
    async (id: number, payload: RoomUpdatePayload) => {
      setSubmitting(true);
      try {
        await updateRoom(id, payload);
        toast.success('교실 정보를 수정했습니다.');
        await load();
        return true;
      } catch (e) {
        toast.error(getApiErrorMessage(e, '교실 수정에 실패했습니다.'));
        return false;
      } finally {
        setSubmitting(false);
      }
    },
    [load],
  );

  const handleDelete = useCallback(
    async (id: number) => {
      if (!window.confirm('정말 이 교실을 삭제하시겠습니까?')) return;
      try {
        await deleteRoom(id);
        toast.success('교실을 삭제했습니다.');
        await load();
      } catch (e) {
        toast.error(getApiErrorMessage(e, '교실 삭제에 실패했습니다.'));
      }
    },
    [load],
  );

  return {
    items,
    loading,
    error,
    keyword,
    setKeyword,
    handleSearch,
    page,
    totalPages,
    setPage,
    submitting,
    handleCreate,
    handleUpdate,
    handleDelete,
  };
}
