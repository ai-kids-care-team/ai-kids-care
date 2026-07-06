import { apiClient } from './apiClient';

/**
 * SPEC-0002 Slice A: 백엔드 `PendingRegistrationVO` (원생 필드, S1 미포함).
 * `AdminKindergartenController` / `KindergartenAdminApprovalService` 계약과 필드명 1:1.
 */
export type PendingRegistrationVO = {
  userId: number;
  role: string;
  /** TEACHER/KINDERGARTEN_ADMIN 신청에만 값 존재(teacher 프로필 level); 그 외 null */
  level: string | null;
  submittedAt: string;
};

/**
 * GET /api/v1/admin/kindergarten/registrations?status=PENDING
 * 본원 PENDING 가입 신청 목록. 백엔드가 세션의 `activeKindergartenId` 로 범위를 강제하므로
 * kindergartenId 를 절대 전달하지 않는다.
 * Slice A는 status=PENDING만 지원(다른 값은 백엔드가 빈 배열 반환) — 페이지네이션 없는 순수 List 응답.
 */
export async function getKindergartenPendingRegistrations(): Promise<PendingRegistrationVO[]> {
  const response = await apiClient.get<PendingRegistrationVO[]>(
    '/admin/kindergarten/registrations',
    { params: { status: 'PENDING' } },
  );
  return response.data;
}

/** POST /api/v1/admin/kindergarten/registrations/{userId}/approve — PENDING → ACTIVE, 204 No Content */
export async function approveKindergartenRegistration(userId: number): Promise<void> {
  await apiClient.post(`/admin/kindergarten/registrations/${userId}/approve`);
}

/** POST /api/v1/admin/kindergarten/registrations/{userId}/reject — PENDING → REJECTED, 204 No Content */
export async function rejectKindergartenRegistration(userId: number): Promise<void> {
  await apiClient.post(`/admin/kindergarten/registrations/${userId}/reject`);
}
