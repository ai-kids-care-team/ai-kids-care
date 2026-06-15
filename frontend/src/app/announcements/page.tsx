import { Suspense } from 'react';
import { AnnouncementsListPage } from '@/components/announcements/AnnouncementsListPage';

export default function AnnouncementsPage() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-gray-50 p-6 text-center text-gray-500">불러오는 중입니다.</div>}>
      <AnnouncementsListPage />
    </Suspense>
  );
}
