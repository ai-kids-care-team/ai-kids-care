// src/app/cctvCamera/page.tsx
import { Suspense } from 'react';

import { CctvDashboardPage } from '@/components/cctv/CctvDashboardPage';

export default function CctvCameraPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-[100dvh] items-center justify-center bg-gray-50 text-center text-gray-500">
          불러오는 중입니다.
        </div>
      }
    >
      <CctvDashboardPage />
    </Suspense>
  );
}