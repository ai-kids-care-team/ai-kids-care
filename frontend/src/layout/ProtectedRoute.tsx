'use client';

import { useEffect, ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useAppSelector } from '@/store/hook';
import { openLoginModal } from '@/utils/auth-modal';

interface ProtectedRouteProps {
  children: ReactNode;
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { user, sessionChecked } = useAppSelector((state) => state.user);
  const router = useRouter();

  useEffect(() => {
    if (sessionChecked && !user) {
      openLoginModal();
      router.replace('/');
    }
  }, [user, router, sessionChecked]);

  if (!sessionChecked || !user) {
    return null;
  }

  return <>{children}</>;
}
