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
import { useAppSelector } from '@/store/hook';
import { resolveViewerSessionKindergartenId } from '@/utils/session-kindergarten';

const PAGE_SIZE = 20;

/**
 * director-operations-ui (C6/UX-05): 카메라 스트림(camera_streams) 목록 + 건/개(create/update).
 *
 * `GET /camera_streams`·`GET /cctv_cameras` 는 컨트롤러 시그니처상 `kindergartenId` 가
 * **필수 쿼리 파라미터**다(서비스가 값이 다르면 404 로 재검증하므로 위조는 불가능하지만, 파라미터
 * 자체는 요구된다) — `CctvDashboardPage.tsx` 가 이미 세션의 `user.kindergartenId` 를 그대로
 * 넘기는 동일 선례를 따른다(신규 도입 아님, 기존 컨트롤러 제약). 반면 create/update 바디에는
 * `kindergartenId` 필드 자체가 없어 절대 전송하지 않는다.
 */
export function useCameraStreamsManagement() {
  const { user } = useAppSelector((state) => state.user);
  const sessionKindergartenId = resolveViewerSessionKindergartenId(user);

  const [items, setItems] = useState<CameraStreamVO[]>([]);
  const [cameras, setCameras] = useState<CctvCameraVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    if (sessionKindergartenId == null) {
      setItems([]);
      setCameras([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const [streamsPage, camerasPage] = await Promise.all([
        getCameraStreamsPage(sessionKindergartenId, page, PAGE_SIZE),
        getCctvCamerasPage(0, 200, sessionKindergartenId),
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
  }, [page, sessionKindergartenId]);

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
    hasSession: sessionKindergartenId != null,
  };
}
