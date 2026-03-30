// src/app/cctvCamera/page.tsx
import { Suspense } from 'react';

/**
 * CCTV 카메라 페이지는 현재 준비 중입니다.
 * (빈 파일이면 Next 빌드 타입체크에서 `is not a module` 오류가 발생합니다.)
 */
export default function CctvCameraPage() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-gray-50 p-6 text-center text-gray-500">불러오는 중입니다.</div>}>
      <div className="min-h-screen bg-gray-50 p-6">
        <div className="mx-auto max-w-3xl rounded-2xl bg-white p-8 text-center shadow-lg">
          CCTV 카메라 기능은 준비 중입니다.
        </div>
      </div>
    </Suspense>
  );
}