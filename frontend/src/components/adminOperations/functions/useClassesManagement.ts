'use client';

import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
import {
  createClass,
  deleteClass,
  getClasses,
  updateClass,
  type ClassCreatePayload,
  type ClassUpdatePayload,
  type ClassVO,
} from '@/services/apis/classes.api';
import { getApiErrorMessage } from '@/components/letters/api-error-message';

const PAGE_SIZE = 10;

/** director-operations-ui (C6/UX-05): 학급(classes) 목록/검색/페이지네이션 + CRUD 오케스트레이션. */
export function useClassesManagement() {
  const [items, setItems] = useState<ClassVO[]>([]);
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
      const data = await getClasses({ keyword: appliedKeyword || undefined, page, size: PAGE_SIZE });
      setItems(data.content);
      setTotalPages(data.totalPages);
    } catch (e) {
      console.warn('학급 목록 조회 실패:', e);
      setError(getApiErrorMessage(e, '학급 목록을 불러오지 못했습니다.'));
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
    async (payload: ClassCreatePayload) => {
      setSubmitting(true);
      try {
        await createClass(payload);
        toast.success('학급을 추가했습니다.');
        setPage(0);
        await load();
        return true;
      } catch (e) {
        toast.error(getApiErrorMessage(e, '학급 추가에 실패했습니다.'));
        return false;
      } finally {
        setSubmitting(false);
      }
    },
    [load],
  );

  const handleUpdate = useCallback(
    async (id: number, payload: ClassUpdatePayload) => {
      setSubmitting(true);
      try {
        await updateClass(id, payload);
        toast.success('학급 정보를 수정했습니다.');
        await load();
        return true;
      } catch (e) {
        toast.error(getApiErrorMessage(e, '학급 수정에 실패했습니다.'));
        return false;
      } finally {
        setSubmitting(false);
      }
    },
    [load],
  );

  const handleDelete = useCallback(
    async (id: number) => {
      if (!window.confirm('정말 이 학급을 삭제하시겠습니까?')) return;
      try {
        await deleteClass(id);
        toast.success('학급을 삭제했습니다.');
        await load();
      } catch (e) {
        toast.error(getApiErrorMessage(e, '학급 삭제에 실패했습니다.'));
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
