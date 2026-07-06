'use client';

import { useAppSelector } from '@/store/hook';
import { openLoginModal } from '@/utils/auth-modal';
import { PendingRegistrationsListForm } from './PendingRegistrationsListForm';
import { usePlatformApprovals } from './functions/usePlatformApprovals';

/**
 * UX-04: 플랫폼 관리자(PLATFORM_IT_ADMIN) SUPERADMIN 가입 승인함.
 * 백엔드 AuthorizationPolicy는 PLATFORM scope + role=PLATFORM_IT_ADMIN 만 허용
 * (SUPERADMIN 본인은 승인 권한 없음) — 프론트 게이트도 동일하게 맞춘다.
 */
export function AdminPlatformApprovalsPage() {
  const { user, isAuthenticated } = useAppSelector((state) => state.user);
  const { items, loading, error, processingUserId, handleApprove, handleReject } =
    usePlatformApprovals();

  if (!isAuthenticated || !user) {
    return (
      <div className="min-h-screen bg-gray-50 p-6">
        <main className="mx-auto max-w-3xl">
          <div className="rounded-2xl bg-white p-8 text-center shadow-lg">
            <p className="mb-4 text-sm text-slate-600">로그인이 필요합니다.</p>
            <button
              type="button"
              onClick={() => openLoginModal()}
              className="rounded-lg bg-[#006b52] px-5 py-2 text-white hover:bg-[#005640]"
            >
              로그인
            </button>
          </div>
        </main>
      </div>
    );
  }

  if (user.role !== 'PLATFORM_IT_ADMIN') {
    return (
      <div className="min-h-screen bg-gray-50 p-6">
        <main className="mx-auto max-w-3xl">
          <div className="rounded-2xl bg-white p-8 text-center shadow-lg">
            <p className="text-sm text-slate-600">
              슈퍼관리자 가입 승인함은 시스템 관리자 계정만 이용할 수 있습니다.
            </p>
          </div>
        </main>
      </div>
    );
  }

  return (
    <PendingRegistrationsListForm
      title="슈퍼관리자 승인함"
      description="플랫폼 슈퍼관리자(행정청) 가입 신청을 승인하거나 거절합니다."
      items={items}
      loading={loading}
      error={error}
      processingUserId={processingUserId}
      onApprove={handleApprove}
      onReject={handleReject}
    />
  );
}
