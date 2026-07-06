'use client';

import { useState } from 'react';
import { Building2 } from 'lucide-react';
import { useAppSelector } from '@/store/hook';
import { openLoginModal } from '@/utils/auth-modal';
import { ClassesSection } from './ClassesSection';
import { RoomsSection } from './RoomsSection';
import { CameraStreamsSection } from './CameraStreamsSection';

type Tab = 'classes' | 'rooms' | 'cameraStreams';

const TABS: { key: Tab; label: string }[] = [
  { key: 'classes', label: '학급' },
  { key: 'rooms', label: '교실' },
  { key: 'cameraStreams', label: '카메라 스트림' },
];

/**
 * director-operations-ui (C6/UX-05): 원장(KINDERGARTEN_ADMIN) 전용 운영 관리 화면 —
 * 학급/교실/카메라 스트림 CRUD 를 탭 하나에 모은다. 백엔드 `@PreAuthorize` 가 최종
 * 권한 판정을 하고, 여기서는 진입점만 거칠게 게이트한다(다른 역할은 메뉴에도 안 보임).
 */
export function OperationsManagementPage() {
  const { user, isAuthenticated } = useAppSelector((state) => state.user);
  const [tab, setTab] = useState<Tab>('classes');

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
            <p className="text-sm text-slate-600">운영 관리는 유치원 관리자(원장) 계정만 이용할 수 있습니다.</p>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-5xl">
        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-6 flex items-center gap-3 border-b border-gray-200 pb-6">
            <Building2 className="h-7 w-7 text-[#006b52]" />
            <div>
              <h2 className="text-3xl">운영 관리</h2>
              <p className="mt-1 text-sm text-gray-500">본원의 학급·교실·카메라 스트림을 등록하고 관리합니다.</p>
            </div>
          </div>

          <div className="mb-6 flex gap-2 border-b border-gray-200">
            {TABS.map((t) => (
              <button
                key={t.key}
                type="button"
                onClick={() => setTab(t.key)}
                className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
                  tab === t.key
                    ? 'border-[#006b52] text-[#006b52]'
                    : 'border-transparent text-gray-500 hover:text-slate-700'
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>

          {tab === 'classes' && <ClassesSection />}
          {tab === 'rooms' && <RoomsSection />}
          {tab === 'cameraStreams' && <CameraStreamsSection />}
        </div>
      </main>
    </div>
  );
}
