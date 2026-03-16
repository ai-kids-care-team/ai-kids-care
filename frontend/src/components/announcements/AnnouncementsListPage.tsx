'use client';

import { AnnouncementsListForm } from './AnnouncementsListForm';
import { useAnnouncements } from './model/useAnnouncements';

export function AnnouncementsListPage() {
  const { announcements, canWrite, loading, error } = useAnnouncements();
  return (
    <AnnouncementsListForm
      announcements={announcements}
      canWrite={canWrite}
      loading={loading}
      error={error}
    />
  );
}
