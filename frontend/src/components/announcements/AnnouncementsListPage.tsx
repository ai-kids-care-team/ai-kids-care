'use client';

import { AnnouncementsListForm } from './AnnouncementsListForm';
import { useAnnouncements } from './model/useAnnouncements';

export function AnnouncementsListPage() {
  const { announcements } = useAnnouncements();
  return <AnnouncementsListForm announcements={announcements} />;
}
