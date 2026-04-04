/* eslint-disable @typescript-eslint/no-misused-promises */
'use client';

import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { getDetectionEventDetail, type DetectionEventDetail } from '@/services/apis/detectionEvents.api';

type UseDetectionEventDetailResult = {
  id: number;
  detail: DetectionEventDetail | null;
  loading: boolean;
  error: string;
  reload: () => Promise<void>;
};

export function useDetectionEventDetail(): UseDetectionEventDetailResult {
  const searchParams = useSearchParams();
  const idParam = searchParams.get('id');
  const id = Number(idParam ?? 0);

  const [detail, setDetail] = useState<DetectionEventDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!Number.isFinite(id) || id <= 0) {
      setDetail(null);
      setError('유효하지 않은 이벤트 ID입니다.');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');
    try {
      const data = await getDetectionEventDetail(id);
      setDetail(data);
    } catch (e) {
      console.error('탐지 이벤트 상세 조회 실패:', e);
      setError('탐지 이벤트 상세 정보를 불러오지 못했습니다.');
      setDetail(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    const resetToTop = () => {
      window.scrollTo({ top: 0, behavior: 'auto' });
      document.documentElement.scrollTop = 0;
      document.body.scrollTop = 0;
      const container = document.getElementById('app-scroll-container');
      if (container) {
        container.scrollTo({ top: 0, behavior: 'auto' });
      }
    };

    resetToTop();
    const frame = window.requestAnimationFrame(resetToTop);
    void load();

    return () => window.cancelAnimationFrame(frame);
  }, [load]);

  return {
    id,
    detail,
    loading,
    error,
    reload: load,
  };
}
