'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/app/context/AuthContext';

export default function Home() {
  // 1. AuthContext에서 다시 isLoading을 가져옵니다.
  const { isAuthenticated, isLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    // 2. 로그인 정보를 아직 확인 중이라면 아무데도 가지 않고 기다립니다.
    if (isLoading) return;

    // 3. 확인이 끝난 후 로그인 여부에 따라 이동시킵니다.
    if (isAuthenticated) {
      router.replace('/dashboard');
    } else {
      router.replace('/login');
    }
  }, [isAuthenticated, isLoading, router]);

  return (
      // 햇살유치원 테마에 맞게 밝은 배경(bg-gray-50)으로 맞췄습니다.
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="flex flex-col items-center gap-4">
          <div className="w-8 h-8 border-4 border-purple-200 border-t-purple-600 rounded-full animate-spin"></div>
          <p className="text-gray-500 font-medium">시스템을 준비 중입니다...</p>
        </div>
      </div>
  );
}


// 'use client';
//
// import { useEffect } from 'react';
// import { useRouter } from 'next/navigation';
// // 경로가 @/app/context/AuthContext 로 정상적으로 맞춰진 상태입니다.
// import { useAuth } from '@/app/context/AuthContext';
//
// export default function Home() {
//   // 1. useAuth()에서 isLoading을 빼고 isAuthenticated만 가져옵니다.
//   const { isAuthenticated } = useAuth();
//   const router = useRouter();
//
//   useEffect(() => {
//     // 2. if (isLoading) return; 구문을 제거합니다.
//     if (isAuthenticated) {
//       router.replace('/dashboard');
//     } else {
//       router.replace('/login');
//     }
//     // 3. 의존성 배열(dependency array)에서도 isLoading을 제거합니다.
//   }, [isAuthenticated, router]);
//
//   return (
//       <div className="min-h-screen flex items-center justify-center bg-slate-900">
//         <p className="text-slate-400">로딩 중...</p>
//       </div>
//   );
// }


// 'use client';
//
// import { useEffect } from 'react';
// import { useRouter } from 'next/navigation';
// import { useAuth } from '@/app/context/AuthContext';
//
// export default function Home() {
//   const { isAuthenticated, isLoading } = useAuth();
//   const router = useRouter();
//
//   useEffect(() => {
//     if (isLoading) return;
//     if (isAuthenticated) {
//       router.replace('/dashboard');
//     } else {
//       router.replace('/login');
//     }
//   }, [isAuthenticated, isLoading, router]);
//
//   return (
//     <div className="min-h-screen flex items-center justify-center bg-slate-900">
//       <p className="text-slate-400">로딩 중...</p>
//     </div>
//   );
// }
