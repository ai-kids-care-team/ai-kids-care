import { apiClient } from './apiClient';
import { API_BASE_URL } from '@/config/api';

/** `evidence_file_type_enum`(백엔드). */
export type EvidenceFileType = 'IMAGE' | 'VIDEO';

/**
 * D-STORE 증거 열람. 백엔드 `EventEvidenceFileVO`(contentPath 추가판)에 대응.
 * kindergartenId/retentionUntil/hold 등 테넌트·보존 필드는 프론트 표시에 쓰지 않으므로 타입에 포함하지 않음.
 */
export type EventEvidenceFileVO = {
  evidenceId: number;
  eventId: number;
  type: EvidenceFileType;
  mimeType: string;
  createdAt: string;
  hash: string;
  /** 백엔드 content 엔드포인트 상대경로(`/api/v1/event_evidence_files/{id}/content`). 구 file:// 행은 null. */
  contentPath: string | null;
  /** 오브젝트 스토리지에서 실제로 읽을 수 있는지(false면 contentPath 있어도 렌더링 금지). */
  available: boolean;
};

// API_BASE_URL(`.../api/v1`)에서 오리진만 뽑아, 백엔드가 내려준 절대경로(contentPath, `/api/v1/...`부터
// 시작)와 합쳐 완전한 URL을 만든다. 프론트/백엔드가 다른 포트로 뜨는 로컬 개발에서도 <img>/<video> src가
// 프론트 오리진이 아니라 백엔드로 향하도록 하기 위함(정적 export라 relative src는 프론트 오리진으로 감).
const API_ORIGIN = API_BASE_URL.replace(/\/api\/v1\/?$/i, '');

/** 증거 content 엔드포인트 상대경로를 브라우저에서 바로 쓸 수 있는 절대 URL로 변환(같은 호스트라 쿠키 인증 그대로 적용). */
export function resolveEvidenceContentUrl(contentPath: string): string {
  return `${API_ORIGIN}${contentPath}`;
}

/**
 * 이벤트의 증거 메타데이터 목록 조회. 이벤트 카드가 확장되거나 상세를 열 때만 호출(리스트 화면에서
 * 일괄 조회 금지). 테넌트 스코프·staff 권한은 백엔드 세션이 강제하므로 kindergartenId는 전송하지 않음.
 * 이벤트에 증거가 없으면 백엔드가 빈 배열을 돌려준다(404 아님).
 */
export async function getEventEvidence(eventId: number): Promise<EventEvidenceFileVO[]> {
  const res = await apiClient.get<EventEvidenceFileVO[]>(`/detection-events/${eventId}/evidence`);
  return res.data;
}
