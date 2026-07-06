import { apiClient } from './apiClient';
import type { PendingRegistrationVO } from './adminKindergarten.api';

export type { PendingRegistrationVO };

/**
 * SPEC-0002 Slice B: `AdminPlatformController` / `PlatformAdminApprovalService` 계약.
 * PLATFORM scope — 대상은 항상 SUPERADMIN 신청(level 은 항상 null).
 */

/**
 * GET /api/v1/admin/platform/superadmin-registrations?status=PENDING
 * PLATFORM scope PENDING SUPERADMIN 신청 목록. kindergartenId 개념 자체가 없음(플랫폼 계정).
 */
export async function getPlatformPendingSuperadminRegistrations(): Promise<PendingRegistrationVO[]> {
  const response = await apiClient.get<PendingRegistrationVO[]>(
    '/admin/platform/superadmin-registrations',
    { params: { status: 'PENDING' } },
  );
  return response.data;
}

/** POST /api/v1/admin/platform/superadmin-registrations/{userId}/approve — PENDING → ACTIVE, 204 No Content */
export async function approvePlatformSuperadminRegistration(userId: number): Promise<void> {
  await apiClient.post(`/admin/platform/superadmin-registrations/${userId}/approve`);
}

/** POST /api/v1/admin/platform/superadmin-registrations/{userId}/reject — PENDING → REJECTED, 204 No Content */
export async function rejectPlatformSuperadminRegistration(userId: number): Promise<void> {
  await apiClient.post(`/admin/platform/superadmin-registrations/${userId}/reject`);
}
