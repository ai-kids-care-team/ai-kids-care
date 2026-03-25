'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { openLoginModal } from '@/utils/auth-modal';

export default function LoginPage() {
  const router = useRouter();

  useEffect(() => {
    openLoginModal();
    router.replace('/');
  }, [router]);

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <p className="text-sm text-gray-500">로그인 창을 여는 중입니다...</p>
    </div>
  );
}