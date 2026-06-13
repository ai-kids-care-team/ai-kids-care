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
