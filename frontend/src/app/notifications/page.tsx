import { Suspense } from 'react';
import { NotificationsListPage } from '@/components/notifications/NotificationsListPage';

export default function NotificationsPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-gray-50 p-6 text-center text-gray-500">
          불러오는 중입니다.
        </div>
      }
    >
      <NotificationsListPage />
    </Suspense>
  );
}
