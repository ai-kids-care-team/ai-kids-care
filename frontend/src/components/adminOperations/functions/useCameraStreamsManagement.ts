'use client';

import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
import {
  createCameraStream,
  getCameraStreamsPage,
  getCctvCamerasPage,
  updateCameraStream,
  type CameraStreamCreatePayload,
  type CameraStreamUpdatePayload,
  type CameraStreamVO,
} from '@/services/apis/cctv.api';
import type { CctvCameraVO } from '@/types/cctv.vo';
import { getApiErrorMessage } from '@/components/letters/api-error-message';

const PAGE_SIZE = 20;

/**
 * director-operations-ui (C6/UX-05): 카메라 스트림(camera_streams) 목록 + 건/개(create/update).
 *
 * camera-endpoint-hygiene (C6-gap-a): `GET /camera_streams`·`GET /cctv_cameras` 는 이제
 * `kindergartenId` 없이 호출한다 — 세션의 ThreadLocal `activeKindergartenId` 가 테넌트를
 * 강제하므로 프론트가 값을 결정/전송할 필요가 없다(이전엔 `resolveViewerSessionKindergartenId`
 * 로 세션 값을 끌어와 쿼리 파라미터로 넘기는 workaround 였음 — 제거). 화면 진입 자체는 부모
 * `OperationsManagementPage`가 이미 `isAuthenticated && role===KINDERGARTEN_ADMIN`로 게이트
 * 하므로 이 훅에서 별도 세션 가드는 두지 않는다(`useClassesManagement`/`useRoomsManagement`와
 * 동일한 패턴). create/update 바디에는 애초에 `kindergartenId` 필드 자체가 없다.
 */
export function useCameraStreamsManagement() {
  const [items, setItems] = useState<CameraStreamVO[]>([]);
  const [cameras, setCameras] = useState<CctvCameraVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [streamsPage, camerasPage] = await Promise.all([
        getCameraStreamsPage(page, PAGE_SIZE),
        getCctvCamerasPage(0, 200),
      ]);
      setItems(streamsPage.content ?? []);
      setTotalPages(streamsPage.totalPages ?? 1);
      setCameras(camerasPage.content ?? []);
    } catch (e) {
      console.warn('카메라 스트림 목록 조회 실패:', e);
      setError(getApiErrorMessage(e, '카메라 스트림 목록을 불러오지 못했습니다.'));
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleCreate = useCallback(
    async (payload: CameraStreamCreatePayload) => {
      setSubmitting(true);
      try {
        await createCameraStream(payload);
        toast.success('카메라 스트림을 추가했습니다.');
        setPage(0);
        await load();
        return true;
      } catch (e) {
        toast.error(getApiErrorMessage(e, '카메라 스트림 추가에 실패했습니다.'));
        return false;
      } finally {
        setSubmitting(false);
      }
    },
    [load],
  );

  const handleUpdate = useCallback(
    async (id: number, payload: CameraStreamUpdatePayload) => {
      setSubmitting(true);
      try {
        await updateCameraStream(id, payload);
        toast.success('카메라 스트림 정보를 수정했습니다.');
        await load();
        return true;
      } catch (e) {
        toast.error(getApiErrorMessage(e, '카메라 스트림 수정에 실패했습니다.'));
        return false;
      } finally {
        setSubmitting(false);
      }
    },
    [load],
  );

  return {
    items,
    cameras,
    loading,
    error,
    page,
    totalPages,
    setPage,
    submitting,
    handleCreate,
    handleUpdate,
  };
}
