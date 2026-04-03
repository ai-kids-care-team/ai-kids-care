import { apiClient } from '@/services/apis/apiClient';
import type { CctvCameraVO, DetectionEventVO, SpringPage } from '@/types/cctv.vo';

export interface CommonCodeVO {
  code: string;
  codeName: string;
  sortOrder?: number;
  isActive?: boolean;
}

export interface CameraStreamVO {
  streamId: number;
  kindergartenId: number;
  cameraId: number;
  streamType: string | null;
  streamUrl: string | null;
  streamUser: string | null;
  streamPasswordEncrypted: string | null;
  protocol: string | null;
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

export async function getDetectionEventsPage(page = 0, size = 200) {
  const { data } = await apiClient.get<SpringPage<DetectionEventVO>>('/detection_events', {
    params: { page, size },
  });
  return data;
}

export async function getDetectionEventTypeCodes() {
  const { data } = await apiClient.get<CommonCodeVO[]>('/common_codes/code_group/detection_events');
  return data;
}

export async function getCameraStreamsPage(page = 0, size = 200) {
  const { data } = await apiClient.get<SpringPage<CameraStreamVO>>('/camera_streams', {
    params: { page, size },
  });
  return data;
}
