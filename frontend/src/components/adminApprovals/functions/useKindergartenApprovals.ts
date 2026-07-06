'use client';

import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
import {
  approveKindergartenRegistration,
  getKindergartenPendingRegistrations,
  rejectKindergartenRegistration,
  type PendingRegistrationVO,
} from '@/services/apis/adminKindergarten.api';
import { getApiErrorMessage } from '@/components/letters/api-error-message';

/** UX-04: 원생 관리자(DIRECTOR/VICE_DIRECTOR) 가입 승인함 상태/액션 훅. */
export function useKindergartenApprovals() {
  const [items, setItems] = useState<PendingRegistrationVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [processingUserId, setProcessingUserId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getKindergartenPendingRegistrations();
      setItems(data);
    } catch (e) {
      console.warn('가입 신청 목록 조회 실패:', e);
      setError(getApiErrorMessage(e, '가입 신청 목록을 불러오지 못했습니다.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleApprove = useCallback(
    async (userId: number) => {
      setProcessingUserId(userId);
      try {
        await approveKindergartenRegistration(userId);
        toast.success('가입 신청을 승인했습니다.');
        setItems((prev) => prev.filter((it) => it.userId !== userId));
      } catch (e) {
        console.warn('가입 승인 실패:', e);
        toast.error(getApiErrorMessage(e, '승인에 실패했습니다.'));
        // 이미 다른 관리자가 처리했거나 상태가 바뀌었을 수 있으므로 목록을 재조회
        void load();
      } finally {
        setProcessingUserId(null);
      }
    },
    [load],
  );

  const handleReject = useCallback(
    async (userId: number) => {
      setProcessingUserId(userId);
      try {
        await rejectKindergartenRegistration(userId);
        toast.success('가입 신청을 거절했습니다.');
        setItems((prev) => prev.filter((it) => it.userId !== userId));
      } catch (e) {
        console.warn('가입 거절 실패:', e);
        toast.error(getApiErrorMessage(e, '거절에 실패했습니다.'));
        void load();
      } finally {
        setProcessingUserId(null);
      }
    },
    [load],
  );

  return { items, loading, error, processingUserId, handleApprove, handleReject };
}
