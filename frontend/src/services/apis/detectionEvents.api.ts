import { apiClient } from './apiClient';

export type DetectionEventListItem = {
  eventId: number;
  kindergartenId: number | null;
  kindergartenName: string | null;
  cameraId: number | null;
  cameraName: string | null;
  roomId: number | null;
  roomName: string | null;
  sessionId: number | null;
  eventType: string | null;
  severity: number | null;
  confidence: number | null;
  detectedAt: string | null;
  startTime: string | null;
  endTime: string | null;
  status: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};



export interface DetectionEventResponse {
    eventId: number;
    kindergartenId: number;
    kindergartenName: string | null;
    cameraId: number;
    cameraName: string | null;
    roomId: number;
    roomName: string | null;
    sessionId: number;
    eventType: string;
    severity: number;
    confidence: number;
    detectedAt: string;
    startTime: string;
    endTime: string;
    status: string;
    createdAt: string;
    updatedAt: string;
}


export type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
};

export const DETECTION_EVENTS_LIST_PAGE_SIZE = 20;

export type GetDetectionEventsParams = {
  keyword?: string;
  page?: number;
  size?: number;
  sort?: string | string[];
};

export type DetectionEventDetail = DetectionEventListItem;
export type DetectionEventUpdateDTO = {
  status: 'OPEN' | 'ACKNOWLEDGED' | 'IN_REVIEW' | 'RESOLVED' | 'DISMISSED' | 'ESCALATED';
};

export async function getDetectionEvents(
  params?: GetDetectionEventsParams,
): Promise<PageResponse<DetectionEventListItem>> {
  const page = params?.page ?? 0;
  const size = params?.size ?? DETECTION_EVENTS_LIST_PAGE_SIZE;
  const keyword = params?.keyword?.trim();
  const sort = params?.sort;

  const response = await apiClient.get<PageResponse<DetectionEventListItem>>('/detection_events', {
    params: {
      page,
      size,
      ...(keyword ? { keyword } : {}),
      ...(sort ? { sort } : {}),
    },
  });

  return response.data;
}

const detectionEventDetailInFlight = new Map<number, Promise<DetectionEventDetail>>();

export async function getDetectionEventDetail(id: number): Promise<DetectionEventDetail> {
  const inFlight = detectionEventDetailInFlight.get(id);
  if (inFlight) return inFlight;

  const request = apiClient
    .get<DetectionEventDetail>(`/detection_events/${id}`)
    .then((response) => response.data)
    .finally(() => {
      detectionEventDetailInFlight.delete(id);
    });

  detectionEventDetailInFlight.set(id, request);
  return request;
}

export async function updateDetectionEvent(
  id: number,
  dto: DetectionEventUpdateDTO,
): Promise<DetectionEventDetail> {
  if (!Number.isFinite(id) || id <= 0) {
    throw new Error('유효하지 않은 이벤트 ID입니다.');
  }

  const payload = {
    status: dto.status,
  };

  const response = await apiClient.put<DetectionEventDetail | { data?: DetectionEventDetail }>(
    `/detection_events/${id}`,
    payload,
  );

  const body: any = response.data;
  const candidate = body?.data ?? body;
  return candidate as DetectionEventDetail;
}

