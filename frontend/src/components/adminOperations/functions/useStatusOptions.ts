'use client';

import { useEffect, useState } from 'react';
import { apiClient } from '@/services/apis/apiClient';

export type StatusOption = { code: string; label: string };

/**
 * ADR-0013: `status` 코드 목록은 `GET /api/v1/enums/status`(백엔드 `StatusEnum`)에서,
 * 한글 라벨은 프론트 i18n(FALLBACK)에서 병합한다. classes/rooms/camera_streams 가 모두
 * 같은 `status` 논리 이름을 공유(`EnumMetadataService` REGISTRY)하므로 훅 하나로 재사용한다.
 * enum 조회가 실패해도 FALLBACK 코드로 폴백해 폼이 막히지 않게 한다.
 */
const FALLBACK_LABELS: Record<string, string> = {
  ACTIVE: '운영중',
  PENDING: '대기',
  DISABLED: '비활성',
  REJECTED: '거절됨',
};

const FALLBACK_OPTIONS: StatusOption[] = Object.entries(FALLBACK_LABELS).map(([code, label]) => ({
  code,
  label,
}));

export function useStatusOptions(): StatusOption[] {
  const [options, setOptions] = useState<StatusOption[]>(FALLBACK_OPTIONS);

  useEffect(() => {
    let cancelled = false;
    apiClient
      .get<{ code: string; sortOrder: number }[]>('/enums/status')
      .then((response) => {
        if (cancelled) return;
        const codes = response.data ?? [];
        if (codes.length === 0) return;
        setOptions(
          codes
            .slice()
            .sort((a, b) => a.sortOrder - b.sortOrder)
            .map((c) => ({ code: c.code, label: FALLBACK_LABELS[c.code] ?? c.code })),
        );
      })
      .catch(() => {
        // enum 엔드포인트 실패 시 FALLBACK_OPTIONS 유지
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return options;
}
