'use client';

import { useAppSelector } from '@/store/hook';
import { openLoginModal } from '@/utils/auth-modal';
import { PendingRegistrationsListForm } from './PendingRegistrationsListForm';
import { useKindergartenApprovals } from './functions/useKindergartenApprovals';

/**
 * UX-04: 원 관리자(KINDERGARTEN_ADMIN, level=DIRECTOR/VICE_DIRECTOR) 가입 승인함.
 * 세밀한 level 검사는 백엔드(KindergartenAdminPolicy)에서 수행 — 프론트는 역할(role)만
 * 거칠게 게이트하고, 자격 미달(level=OTHER 등)이면 서버 403을 에러 배너로 보여준다.
 */
export function AdminKindergartenApprovalsPage() {
  const { user, isAuthenticated } = useAppSelector((state) => state.user);
  const { items, loading, error, processingUserId, handleApprove, handleReject } =
    useKindergartenApprovals();

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

  if (user.role !== 'KINDERGARTEN_ADMIN') {
    return (
      <div className="min-h-screen bg-gray-50 p-6">
        <main className="mx-auto max-w-3xl">
          <div className="rounded-2xl bg-white p-8 text-center shadow-lg">
            <p className="text-sm text-slate-600">
              가입 승인함은 유치원 관리자(원장·부원장) 계정만 이용할 수 있습니다.
            </p>
          </div>
        </main>
      </div>
    );
  }

  return (
    <PendingRegistrationsListForm
      title="가입 승인함"
      description="본원 소속 교사·학부모 가입 신청을 승인하거나 거절합니다."
      items={items}
      loading={loading}
      error={error}
      processingUserId={processingUserId}
      onApprove={handleApprove}
      onReject={handleReject}
    />
  );
}
