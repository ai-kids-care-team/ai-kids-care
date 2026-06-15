/**
 * 백엔드 `CctvCameraVO` / `DetectionEventVO` / Spring Data `Page` JSON 과 맞춘 타입.
 */

export type CctvCameraStatus = 'ACTIVE' | 'PENDING' | 'DISABLED';

export interface CctvCameraVO {
  cameraId: number;
  kindergartenId: number;
  serialNo: string | null;
  cameraName: string;
  model: string | null;
  createdByUserId: number;
  status: CctvCameraStatus;
  lastSeenAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export type DetectionEventType =
  | 'ASSAULT'
  | 'FIGHT'
  | 'BURGLARY'
  | 'VANDALISM'
  | 'SWOON'
  | 'WANDER'
  | 'TRESPASS'
  | 'DUMP'
  | 'ROBBERY'
  | 'DATEFIGHT'
  | 'KIDNAP'
  | 'DRUNKEN'
  | 'OTHER';

export type DetectionEventStatus =
  | 'OPEN'
  | 'ACKNOWLEDGED'
  | 'IN_REVIEW'
  | 'RESOLVED'
  | 'DISMISSED'
  | 'ESCALATED';

export interface DetectionEventVO {
  eventId: number;
  kindergartenId: number;
  cameraId: number;
  roomId: number;
  sessionId: number;
  eventType: DetectionEventType;
  severity: number;
  confidence: number;
  detectedAt: string;
  startTime: string;
  endTime: string;
  status: DetectionEventStatus;
  createdAt: string;
  updatedAt: string;
}

export interface SpringPage<T> {
  content: T[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
}
