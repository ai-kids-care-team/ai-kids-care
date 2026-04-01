import { apiClient } from '@/services/apis/apiClient';
import type { CctvCameraVO, DetectionEventVO, SpringPage } from '@/types/cctv.vo';

export async function getCctvCamerasPage(page = 0, size = 100) {
  const { data } = await apiClient.get<SpringPage<CctvCameraVO>>('/cctv_cameras', {
    params: { page, size },
  });
  return data;
}

export async function getDetectionEventsPage(page = 0, size = 200) {
  const { data } = await apiClient.get<SpringPage<DetectionEventVO>>('/detection_events', {
    params: { page, size },
  });
  return data;
}
