import { apiClient } from '@/services/apis/apiClient';
import type { CctvCameraVO, SpringPage } from '@/types/cctv.vo';

export interface CameraStreamVO {
  streamId: number;
  kindergartenId: number;
  cameraId: number;
  streamType: string | null;
  hasPassword: boolean | null;
  sourceProtocol: string | null;
  playbackProtocol: string | null;
  fps: number | null;
  resolution: string | null;
  isPrimary: boolean | null;
  enabled: boolean | null;
  status: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export async function getCctvCamerasPage(page = 0, size = 100, kindergartenId?: number) {
  const { data } = await apiClient.get<SpringPage<CctvCameraVO>>('/cctv_cameras', {
    params: {
      page,
      size,
      ...(kindergartenId != null && Number.isFinite(kindergartenId) ? { kindergartenId } : {}),
    },
  });
  return data;
}

export async function getCameraStreamsPage(
  kindergartenId: number,
  page = 0,
  size = 200,
) {
  const { data } = await apiClient.get<SpringPage<CameraStreamVO>>('/camera_streams', {
    params: { kindergartenId, page, size },
  });
  return data;
}

/**
 * director-operations-ui (C6/UX-05): `POST/PUT /api/v1/camera_streams` 쓰기.
 *
 * 백엔드 `CameraStreamCreateRequest`/`CameraStreamUpdateRequest` 와 1:1 — `kindergartenId`
 * 필드 자체가 DTO에 없다(세션의 `activeKindergartenId` 로 서버가 강제). `streamPassword` 는
 * `@JsonProperty(WRITE_ONLY)`라 응답에 절대 돌아오지 않는다 — 전송만 하고 화면에 표시하지 않는다.
 * `sourceUrl`/`streamUser` 도 `CameraStreamVO` 에 필드 자체가 없어(OpenAPI 은닉과 별개로 응답
 * 자체가 미포함) 수정 폼에서 항상 빈 값으로 시작한다; 빈 문자열을 보내면 PATCH 의미상 기존 값을
 * 지우게 되므로 값을 바꾸지 않을 필드는 payload 자체에서 키를 생략해야 한다.
 */
export type CameraStreamCreatePayload = {
  cameraId: number;
  streamType?: string | null;
  sourceUrl?: string;
  streamUser?: string;
  streamPassword?: string;
  sourceProtocol?: string | null;
  playbackUrl?: string | null;
  playbackProtocol?: string | null;
  fps?: number | null;
  resolution?: string | null;
  isPrimary?: boolean | null;
  enabled?: boolean | null;
};

/** `CameraStreamUpdateRequest` — cameraId 는 update 대상에서 제외(연결 변경 불가). */
export type CameraStreamUpdatePayload = Omit<CameraStreamCreatePayload, 'cameraId'>;

export async function createCameraStream(
  payload: CameraStreamCreatePayload,
): Promise<CameraStreamVO> {
  const { data } = await apiClient.post<CameraStreamVO>('/camera_streams', payload);
  return data;
}

export async function updateCameraStream(
  id: number,
  payload: CameraStreamUpdatePayload,
): Promise<CameraStreamVO> {
  const { data } = await apiClient.put<CameraStreamVO>(`/camera_streams/${id}`, payload);
  return data;
}
