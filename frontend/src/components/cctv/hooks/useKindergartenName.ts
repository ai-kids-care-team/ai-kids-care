'use client';

import { useEffect, useState } from 'react';
import { getKindergarten } from '@/services/apis/kindergartens.api';

/**
 * 주어진 유치원 id의 표시용 이름을 비동기로 해석한다(CctvDashboardPage 원본 :266-316의 두
 * 거의 동일한 effect — 세션 소속 유치원용/선택된 카메라 소속 유치원용 — 를 통합).
 *
 * id가 없거나(≤0) 아직 응답이 없으면 `null`을 반환하며, 호출측은 이를 "불러오는 중…" 등으로
 * 표시하면 된다. 조회가 실패해도(예: 권한 문제) 이름 자리에 `유치원 #{id}` 플레이스홀더로
 * 대체해 UI가 영구히 "불러오는 중…" 상태로 멈추지 않게 한다(원본 동작과 동일).
 */
export function useKindergartenName(id: number | null | undefined): string | null {
  const [resolved, setResolved] = useState<{ id: number; name: string } | null>(null);

  useEffect(() => {
    if (id == null || id <= 0) return;
    let cancelled = false;
    void getKindergarten(id)
      .then((kg) => {
        if (!cancelled) {
          setResolved({ id, name: kg.name?.trim() || `유치원 #${id}` });
        }
      })
      .catch(() => {
        if (!cancelled) {
          setResolved({ id, name: `유치원 #${id}` });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (id == null || id <= 0) return null;
  return resolved?.id === id ? resolved.name : null;
}
