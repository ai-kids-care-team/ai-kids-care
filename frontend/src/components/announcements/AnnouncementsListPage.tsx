'use client';

import { AnnouncementsListForm } from './AnnouncementsListForm';
import { useAnnouncements } from './model/useAnnouncements';

export function AnnouncementsListPage() {
  const { announcements, keyword, setKeyword, handleSearch, canWrite, loading, error } = useAnnouncements();
  return (
    <AnnouncementsListForm
      announcements={announcements}
      keyword={keyword}
      onKeywordChange={setKeyword}
      onSearch={handleSearch}
      canWrite={canWrite}
      loading={loading}
      error={error}
    />
  );
}
