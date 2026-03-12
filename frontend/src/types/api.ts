// src/app/types/api.ts

/**
 * 전역 상태 Enum
 * OpenAPI 명세서 및 최신 DBML 기준에 맞춰 모든 상태 필드는 이 3가지 중 하나를 가집니다.
 */
export type SystemStatus = 'ACTIVE' | 'PENDING' | 'DISABLED';

/**
 * 성별 공통 코드
 */
export type Gender = 'MALE' | 'FEMALE';

/**
 * API 공통 페이징 응답 구조 (목록 조회 시 사용)
 */
export interface PaginatedResponse<T> {
  data: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  size: number;
}

// ============================================================================
// 1. 핵심 엔티티 (Core Entities)
// ============================================================================

export interface Kindergarten {
  id: string; // API URL 경로의 {kindergartenId}
  name: string;
  address: string | null;
  phone: string | null;
  status: SystemStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Class {
  id: string;
  kindergartenId: string;
  name: string; // 예: "햇살반"
  description: string | null;
  status: SystemStatus;
  createdAt: string;
}

export interface Child {
  id: string; // API URL 경로의 {childId}
  kindergartenId: string;
  name: string;
  childNo: string | null; // 원아 번호
  birthDate: string | null; // YYYY-MM-DD
  gender: Gender | null;
  status: SystemStatus;
  createdAt: string;
}

export interface Teacher {
  id: string; // API URL 경로의 {teacherId}
  kindergartenId: string;
  userId: string; // Auth 시스템의 실제 User 계정 ID와 연결됨
  name: string;
  phone: string | null;
  status: SystemStatus;
  createdAt: string;
}

export interface Guardian {
  id: string; // API URL 경로의 {guardianId}
  kindergartenId: string;
  userId: string;
  name: string;
  phone: string | null;
  status: SystemStatus;
  createdAt: string;
}

// ============================================================================
// 2. 물리적 인프라 (Infrastructure)
// ============================================================================

export type RoomType = 'CLASSROOM' | 'PLAYGROUND' | 'CAFETERIA' | 'CORRIDOR' | 'ENTRANCE' | 'PARKING' | 'OFFICE' | 'OTHER';

export interface Room {
  id: string;
  kindergartenId: string;
  name: string; // 예: "2층 201호", "야외 놀이터"
  roomType: RoomType;
  status: SystemStatus;
  createdAt: string;
}

export interface Camera {
  id: string; // API URL 경로의 {cameraId}
  kindergartenId: string;
  name: string; // 예: "정문 입구 카메라 1"
  streamUrl: string | null; // HLS 또는 RTSP 실시간 스트리밍 주소
  status: SystemStatus;
  isRecording: boolean;
  createdAt: string;
}

// ============================================================================
// 3. 관계 매핑 (Assignments & Relationships) - OpenAPI N:M 매핑용
// ============================================================================

export interface ChildClassAssignment {
  id: string; // {assignmentId}
  childId: string;
  classId: string;
  assignedAt: string;
  status: SystemStatus;
}

export interface ClassTeacherAssignment {
  id: string;
  classId: string;
  teacherId: string;
  isMainTeacher: boolean; // 담임(true) / 부담임(false) 여부
  assignedAt: string;
  status: SystemStatus;
}

export interface RoomCameraAssignment {
  id: string;
  roomId: string;
  cameraId: string;
  assignedAt: string;
  status: SystemStatus;
}

export interface ChildGuardianRelationship {
  id: string;
  childId: string;
  guardianId: string;
  relationshipCode: string; // 'MOTHER', 'FATHER', 'GRANDMOTHER' 등
  isPrimary: boolean; // 주 보호자 여부 (긴급 알림 수신 우선순위)
  status: SystemStatus;
}

// ============================================================================
// 4. AI 및 알림 시스템 (AI & Notifications)
// ============================================================================

export interface AIModel {
  id: string;
  name: string; // 예: "Fall Detection", "Violence Detection"
  version: string;
  isActive: boolean;
  description: string | null;
}

export interface DetectionSession {
  id: string;
  kindergartenId: string;
  cameraId: string;
  modelId: string;
  startedAt: string;
  endedAt: string | null;
  status: SystemStatus; // ACTIVE면 현재 AI가 영상을 분석 중임을 의미
}

export interface EventLog {
  id: string;
  kindergartenId: string;
  cameraId: string;
  eventType: string; // AnomalyType 과 유사
  confidenceScore: number; // AI 감지 확신도 (0~100)
  detectedAt: string; // 감지된 정확한 타임스탬프
  videoSnippetUrl: string | null; // 사고 발생 전후 10초 짧은 영상 클립 URL
  isReviewed: boolean; // 관리자나 교사가 확인했는지 여부
}

export interface Notification {
  id: string;
  kindergartenId: string;
  recipientId: string; // 받을 사람 (교사 또는 보호자)의 User ID
  eventId: string | null;
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

// ============================================================================
// 5. 대시보드 (Dashboard)
// ============================================================================

export interface DashboardMetric {
  id?: string | number;   // 👈 테이블 key와 id 출력용
  title?: string;
  metricName?: string;    // 👈 테이블 이름 출력용
  value: number;
  unit?: string;
  trend?: string;
  trendValue?: string;
  [key: string]: any;     // 👈 기타 미정의 속성 통과용
}