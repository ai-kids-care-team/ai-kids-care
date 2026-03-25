'use client';

import { useEffect, ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useAppSelector } from '@/store/hook';
import { openLoginModal } from '@/utils/auth-modal';

interface ProtectedRouteProps {
  children: ReactNode;
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { user } = useAppSelector((state) => state.user);
  const router = useRouter();

  useEffect(() => {
    if (!user) {
      openLoginModal();
      router.replace('/');
    }
  }, [user, router]);

  if (!user) {
    return null;
  }

  return <>{children}</>;
}