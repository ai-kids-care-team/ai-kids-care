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
 * kindergartenId 미확정이면 두 조회 모두 건너뛰고 빈 상태를 유지한다.
 *
 * 원본의 `window.setTimeout(..., 0)` 래핑은 제거했다 — effect 안에서 비동기 함수를 직접
 * 호출하는 것은 React 규칙상 문제 없고, setTimeout 은 굳이 다음 tick으로 미룰 이유가 없었다.
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
    setLoading(true);
    if (!canView || kindergartenId == null) {
      setCameras([]);
      setLoading(false);
      return;
    }
    void getCctvCamerasPage(0, 200, kindergartenId)
      .then((cameraPage) => {
        if (!cancelled) setCameras(cameraPage?.content ?? []);
      })
      .catch(() => {
        if (!cancelled) {
          setCameras([]);
          console.warn('카메라 목록 조회에 실패했습니다.');
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [canView, kindergartenId]);

  useEffect(() => {
    let cancelled = false;
    if (!canView || kindergartenId == null || kindergartenId <= 0) {
      setStreamMapByCamera(new Map());
      return;
    }
    void getCameraStreamsPage(kindergartenId, 0, 500)
      .then((page) => {
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
      })
      .catch((err) => {
        console.warn('camera_streams 조회 실패: /camera_streams', err);
        if (!cancelled) setStreamMapByCamera(new Map());
      });
    return () => {
      cancelled = true;
    };
  }, [canView, kindergartenId]);

  return { cameras, loading, streamMapByCamera };
}
