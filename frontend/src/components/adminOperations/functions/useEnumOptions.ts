'use client';

import { useEffect, useState } from 'react';
import { apiClient } from '@/services/apis/apiClient';

export type EnumOption = { code: string; label: string };

/**
 * camera-endpoint-hygiene (C6-gap-b): `GET /api/v1/enums/{name}`(백엔드 `EnumMetadataService`
 * REGISTRY)에서 코드 목록을, 프론트 i18n `fallbackLabels`에서 라벨을 병합한다.
 * `useStatusOptions.ts`와 동일한 fetch+FALLBACK 패턴을 임의의 enum 이름에 재사용할 수 있게
 * 일반화했다 — enum 조회가 실패해도 fallback 코드로 폴백해 폼이 막히지 않는다.
 *
 * `fallbackLabels`는 코드→라벨 병합에만 쓰고 effect 안에서 참조하지 않는다(호출부가 매 렌더
 * 새 객체 리터럴을 넘겨도 재조회가 트리거되지 않도록 — exhaustive-deps 경고 회피 목적의
 * eslint-disable 없이 안전하게 유지).
 */
export function useEnumOptions(
  enumName: string,
  fallbackLabels: Record<string, string>,
): EnumOption[] {
  const [codes, setCodes] = useState<string[]>(() => Object.keys(fallbackLabels));

  useEffect(() => {
    let cancelled = false;
    apiClient
      .get<{ code: string; sortOrder: number }[]>(`/enums/${enumName}`)
      .then((response) => {
        if (cancelled) return;
        const data = response.data ?? [];
        if (data.length === 0) return;
        setCodes(
          data
            .slice()
            .sort((a, b) => a.sortOrder - b.sortOrder)
            .map((c) => c.code),
        );
      })
      .catch(() => {
        // enum 엔드포인트 실패 시 fallback 코드 유지
      });
    return () => {
      cancelled = true;
    };
  }, [enumName]);

  return codes.map((code) => ({ code, label: fallbackLabels[code] ?? code }));
}
