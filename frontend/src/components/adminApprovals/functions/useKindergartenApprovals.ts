'use client';

import {
  approveKindergartenRegistration,
  getKindergartenPendingRegistrations,
  rejectKindergartenRegistration,
  type PendingRegistrationVO,
} from '@/services/apis/adminKindergarten.api';
import { useApprovalQueue } from './useApprovalQueue';

/**
 * UX-04: 원생 관리자(DIRECTOR/VICE_DIRECTOR) 가입 승인함 상태/액션 훅.
 *
 * refactor-cross-cutting-debt (QLT-01 / D3): thin wrapper over the generic `useApprovalQueue` —
 * the two hooks' outward shape were already field-for-field identical, so this returns the
 * generic's result unchanged.
 */
export function useKindergartenApprovals() {
  return useApprovalQueue<PendingRegistrationVO>({
    list: getKindergartenPendingRegistrations,
    approve: approveKindergartenRegistration,
    reject: rejectKindergartenRegistration,
    labels: {
      loadErrorLog: '가입 신청 목록 조회 실패:',
      loadErrorToast: '가입 신청 목록을 불러오지 못했습니다.',
      approveSuccess: '가입 신청을 승인했습니다.',
      approveErrorLog: '가입 승인 실패:',
      approveErrorToast: '승인에 실패했습니다.',
      rejectSuccess: '가입 신청을 거절했습니다.',
      rejectErrorLog: '가입 거절 실패:',
      rejectErrorToast: '거절에 실패했습니다.',
    },
  });
}
