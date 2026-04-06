import { Suspense } from 'react';

import { LettersPageClient } from './LettersPageClient';

export default function LettersPage() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-gray-50 p-6 text-center text-gray-500">불러오는 중입니다.</div>}>
      <LettersPageClient />
    </Suspense>
  );
}
