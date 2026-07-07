'use client';

import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
import { getApiErrorMessage } from '@/components/letters/api-error-message';

export interface ApprovalQueueLabels {
  /** console.warn prefix on load failure. */
  loadErrorLog: string;
  /** toast/error fallback text on load failure. */
  loadErrorToast: string;
  approveSuccess: string;
  /** console.warn prefix on approve failure. */
  approveErrorLog: string;
  approveErrorToast: string;
  rejectSuccess: string;
  /** console.warn prefix on reject failure. */
  rejectErrorLog: string;
  rejectErrorToast: string;
}

export interface UseApprovalQueueConfig<TItem extends { userId: number }> {
  list: () => Promise<TItem[]>;
  approve: (userId: number) => Promise<unknown>;
  reject: (userId: number) => Promise<unknown>;
  labels: ApprovalQueueLabels;
}

/**
 * refactor-cross-cutting-debt (QLT-01 / D3): generalizes the near-identical pending-approval
 * queue orchestration previously duplicated line-for-line across `useKindergartenApprovals`
 * (원생 admin) and `usePlatformApprovals` (플랫폼 SUPERADMIN).
 *
 * Both wrapper hooks return this hook's result unchanged (their outward shape was already
 * identical field-for-field), so this generic's return IS the outward contract.
 */
export function useApprovalQueue<TItem extends { userId: number }>(config: UseApprovalQueueConfig<TItem>) {
  const { list, approve, reject, labels } = config;

  const [items, setItems] = useState<TItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [processingUserId, setProcessingUserId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await list();
      setItems(data);
    } catch (e) {
      console.warn(labels.loadErrorLog, e);
      setError(getApiErrorMessage(e, labels.loadErrorToast));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleApprove = useCallback(
    async (userId: number) => {
      setProcessingUserId(userId);
      try {
        await approve(userId);
        toast.success(labels.approveSuccess);
        setItems((prev) => prev.filter((it) => it.userId !== userId));
      } catch (e) {
        console.warn(labels.approveErrorLog, e);
        toast.error(getApiErrorMessage(e, labels.approveErrorToast));
        // 이미 다른 관리자가 처리했거나 상태가 바뀌었을 수 있으므로 목록을 재조회
        void load();
      } finally {
        setProcessingUserId(null);
      }
    },
    [approve, load, labels.approveSuccess, labels.approveErrorLog, labels.approveErrorToast],
  );

  const handleReject = useCallback(
    async (userId: number) => {
      setProcessingUserId(userId);
      try {
        await reject(userId);
        toast.success(labels.rejectSuccess);
        setItems((prev) => prev.filter((it) => it.userId !== userId));
      } catch (e) {
        console.warn(labels.rejectErrorLog, e);
        toast.error(getApiErrorMessage(e, labels.rejectErrorToast));
        void load();
      } finally {
        setProcessingUserId(null);
      }
    },
    [reject, load, labels.rejectSuccess, labels.rejectErrorLog, labels.rejectErrorToast],
  );

  return { items, loading, error, processingUserId, handleApprove, handleReject };
}
