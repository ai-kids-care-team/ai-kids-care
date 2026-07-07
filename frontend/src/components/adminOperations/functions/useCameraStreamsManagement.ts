'use client';

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
import { useCrudResource } from './useCrudResource';

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
 *
 * refactor-cross-cutting-debt (QLT-01 / D3): thin wrapper over the generic `useCrudResource` —
 * outward return shape is unchanged from before the refactor, so consuming components
 * (`CameraStreamsSection`) need zero edits. This resource has no keyword search and no delete
 * action, and its load also fetches the sibling camera catalog (`extra`) in parallel — the
 * generic hook's `extra` escape hatch carries that through, renamed to `cameras` below.
 */
export function useCameraStreamsManagement() {
  const resource = useCrudResource<CameraStreamVO, CameraStreamCreatePayload, CameraStreamUpdatePayload, CctvCameraVO[]>(
    {
      list: async ({ page, size }) => {
        const [streamsPage, camerasPage] = await Promise.all([
          getCameraStreamsPage(page, size),
          getCctvCamerasPage(0, 200),
        ]);
        return {
          content: streamsPage.content ?? [],
          totalPages: streamsPage.totalPages ?? 1,
          extra: camerasPage.content ?? [],
        };
      },
      create: createCameraStream,
      update: updateCameraStream,
      pageSize: PAGE_SIZE,
      hasKeyword: false,
      hasDelete: false,
      labels: {
        loadErrorLog: '카메라 스트림 목록 조회 실패:',
        loadErrorToast: '카메라 스트림 목록을 불러오지 못했습니다.',
        createSuccess: '카메라 스트림을 추가했습니다.',
        createErrorToast: '카메라 스트림 추가에 실패했습니다.',
        updateSuccess: '카메라 스트림 정보를 수정했습니다.',
        updateErrorToast: '카메라 스트림 수정에 실패했습니다.',
      },
    },
  );

  return {
    items: resource.items,
    cameras: resource.extra ?? [],
    loading: resource.loading,
    error: resource.error,
    page: resource.page,
    totalPages: resource.totalPages,
    setPage: resource.setPage,
    submitting: resource.submitting,
    handleCreate: resource.handleCreate,
    handleUpdate: resource.handleUpdate,
  };
}
