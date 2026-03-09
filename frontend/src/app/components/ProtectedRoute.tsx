'use client'; // 👈 1. 클라이언트 컴포넌트 선언

import { useEffect } from 'react';
import { useRouter } from 'next/navigation'; // 👈 2. Next.js 라우터 가져오기
import { useAuth } from '@/app/context/AuthContext';
import { ReactNode } from 'react';

interface ProtectedRouteProps {
  children: ReactNode;
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated } = useAuth();
  const router = useRouter();

  useEffect(() => {
    // 3. 인증되지 않은 사용자인 경우 로그인 페이지로 리다이렉트
    if (!isAuthenticated) {
      router.replace('/login');
    }
  }, [isAuthenticated, router]);

  // 4. 리다이렉트가 진행되는 동안 화면에 권한 없는 내용이 깜빡이지 않도록 null 반환
  if (!isAuthenticated) {
    return null; // (또는 <p>로딩 중...</p> 같은 스피너를 넣으셔도 좋습니다)
  }

  return <>{children}</>;
}

// import { Navigate } from 'react-router';
// import { useAuth } from '../contexts/AuthContext';
// import { ReactNode } from 'react';
//
// interface ProtectedRouteProps {
//   children: ReactNode;
// }
//
// export function ProtectedRoute({ children }: ProtectedRouteProps) {
//   const { isAuthenticated } = useAuth();
//
//   if (!isAuthenticated) {
//     return <Navigate to="/login" replace />;
//   }
//
//   return <>{children}</>;
// }
