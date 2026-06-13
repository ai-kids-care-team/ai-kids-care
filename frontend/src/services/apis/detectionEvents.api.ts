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

/** 목록 화면 한 페이지당 건수 */
export const DETECTION_EVENTS_LIST_PAGE_SIZE = 4;

export type GetDetectionEventsParams = {
  kindergartenId: number;
  keyword?: string;
  page?: number;
  size?: number;
  sort?: string | string[];
};

export type DetectionEventDetail = DetectionEventListItem;

export async function getDetectionEvents(
  params: GetDetectionEventsParams,
): Promise<PageResponse<DetectionEventListItem>> {
  void params;
  throw new Error('Detection event reads are unavailable until tenant authorization exists');
}

export async function getDetectionEventDetail(
  id: number,
  kindergartenId: number,
): Promise<DetectionEventDetail> {
  void id;
  void kindergartenId;
  throw new Error('Detection event reads are unavailable until tenant authorization exists');
}

