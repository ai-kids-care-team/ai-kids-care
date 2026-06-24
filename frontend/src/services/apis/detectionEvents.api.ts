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

/** 목록 화면 한 페이지당 건수 */
export const DETECTION_EVENTS_LIST_PAGE_SIZE = 4;

/** 대시보드가 연결 시 불러오는 최근 이력 건수(이후 SSE 증분). */
export const DETECTION_EVENTS_RECENT_SIZE = 20;

export type GetDetectionEventsParams = {
  /** 백엔드가 세션의 active kindergarten으로 스코프하므로 클라이언트는 보낼 필요가 없습니다(선택). */
  kindergartenId?: number;
  keyword?: string;
  page?: number;
  size?: number;
  sort?: string | string[];
};

export type DetectionEventDetail = DetectionEventListItem;

/**
 * 본인 유치원의 감지 이벤트 목록(최신순). 테넌트 스코프는 백엔드가 세션의 active kindergarten으로
 * 강제하므로 kindergartenId는 전송하지 않습니다.
 */
export async function getDetectionEvents(
  params: GetDetectionEventsParams = {},
): Promise<PageResponse<DetectionEventListItem>> {
  const { keyword, page, size, sort } = params;
  const res = await apiClient.get<PageResponse<DetectionEventListItem>>('/detection-events', {
    params: { keyword, page, size, sort },
  });
  return res.data;
}

export async function getDetectionEventDetail(
  id: number,
  kindergartenId?: number,
): Promise<DetectionEventDetail> {
  void kindergartenId; // 백엔드가 세션 스코프로 강제 — 전송 불필요
  const res = await apiClient.get<DetectionEventDetail>(`/detection-events/${id}`);
  return res.data;
}
