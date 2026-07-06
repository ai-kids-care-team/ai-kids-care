'use client';

import { useEffect, useState } from 'react';
import { getCameraStreamsPage, getCctvCamerasPage } from '@/services/apis/cctv.api';
import type { CameraStreamVO } from '@/services/apis/cctv.api';
import type { CctvCameraVO } from '@/types/cctv.vo';

export type UseCctvCamerasResult = {
  cameras: CctvCameraVO[];
  loading: boolean;
  streamMapByCamera: Map<number, CameraStreamVO[]>;
};

/**
 * CCTV 카메라 목록 + `camera_streams` 조회(원본 CctvDashboardPage :333-350 카메라 부분 +
 * :484-514 스트림 부분을 통합). `canView=false`(live-stream 권한 없는 역할) 또는
 * kindergartenId 미확정이면 두 조회 모두 건너뛰고 빈 상태를 유지한다 — 이 `kindergartenId`
 * 는 "이 역할에 조회할 유치원이 있는가"를 판단하는 UX 게이트일 뿐, camera-endpoint-hygiene
 * (C6-gap-a) 이후로는 `getCctvCamerasPage`/`getCameraStreamsPage` 요청 자체에는 실리지
 * 않는다(세션의 ThreadLocal `activeKindergartenId`가 테넌트를 강제).
 *
 * 원본의 `window.setTimeout(..., 0)` 래핑은 제거했다 — 실제로는 데드 코드가 아니라
 * `react-hooks/set-state-in-effect`(React 19 lint 규칙, effect 본문에서 직접 setState 호출을
 * 금지)를 우회하던 방편이었다. 여기서는 `DetectionEventsDashboard.tsx`가 쓰는 정석 패턴 —
 * effect 안에서 async IIFE를 즉시 호출 — 으로 대체했다(동일하게 lint 통과, setTimeout(0) 틱
 * 지연은 없음).
 */
export function useCctvCameras(
  kindergartenId: number | null,
  canView: boolean,
): UseCctvCamerasResult {
  const [cameras, setCameras] = useState<CctvCameraVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [streamMapByCamera, setStreamMapByCamera] = useState<Map<number, CameraStreamVO[]>>(
    new Map(),
  );

  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (!canView || kindergartenId == null) {
        if (!cancelled) {
          setCameras([]);
          setLoading(false);
        }
        return;
      }
      setLoading(true);
      try {
        const cameraPage = await getCctvCamerasPage(0, 200);
        if (!cancelled) setCameras(cameraPage?.content ?? []);
      } catch {
        if (!cancelled) {
          setCameras([]);
          console.warn('카메라 목록 조회에 실패했습니다.');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [canView, kindergartenId]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (!canView || kindergartenId == null || kindergartenId <= 0) {
        if (!cancelled) setStreamMapByCamera(new Map());
        return;
      }
      try {
        const page = await getCameraStreamsPage(0, 500);
        if (cancelled) return;
        const source = page?.content ?? [];
        const m = new Map<number, CameraStreamVO[]>();
        for (const row of source) {
          const list = m.get(row.cameraId) ?? [];
          list.push(row);
          m.set(row.cameraId, list);
        }
        if (m.size === 0) {
          console.warn('camera_streams loaded but empty (streamMapByCamera size=0)');
        }
        setStreamMapByCamera(m);
      } catch (err) {
        console.warn('camera_streams 조회 실패: /camera_streams', err);
        if (!cancelled) setStreamMapByCamera(new Map());
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [canView, kindergartenId]);

  return { cameras, loading, streamMapByCamera };
}
