export type DetectionEventItem = {
  id: number;
  kindergartenId: number | null;
  kindergartenName: string | null;
  cameraId: number | null;
  cameraName: string | null;
  roomId: number | null;
  roomName: string | null;
  sessionId: number | null;
  eventType: string | null;
  eventTypeName: string | null;
  severity: number | null;
  confidence: number | null;
  detectedAt: string;
  startTime: string | null;
  endTime: string | null;
  status: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  href: string;
};

export type DetectionEventStatus =
  | 'OPEN'
  | 'ACKNOWLEDGED'
  | 'IN_REVIEW'
  | 'RESOLVED'
  | 'DISMISSED'
  | 'ESCALATED';

