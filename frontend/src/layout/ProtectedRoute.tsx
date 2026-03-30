'use client';

import { ReactNode } from 'react';
import { useAppSelector } from '@/store/hook';
import { openLoginModal } from '@/utils/auth-modal';
import { Button } from '@/components/shared/ui/button';

interface ProtectedRouteProps {
  children: ReactNode;
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { user } = useAppSelector((state) => state.user);

  if (!user) {
    return (
      <div className="flex min-h-[50vh] flex-col items-center justify-center gap-4 p-8 text-center">
        <p className="text-slate-600">로그인이 필요합니다.</p>
        <Button type="button" onClick={() => openLoginModal()} className="bg-[#006b52] hover:bg-[#005640]">
          로그인
        </Button>
      </div>
    );
  }

  return <>{children}</>;
}
