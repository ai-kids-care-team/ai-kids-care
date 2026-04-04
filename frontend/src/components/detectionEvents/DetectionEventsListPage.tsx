'use client';

import { DetectionEventsListForm } from './DetectionEventsListForm';
import { useDetectionEvents } from '@/components/detectionEvents/functions/useDetectionEvents';

export function DetectionEventsListPage() {
  const {
    events,
    keyword,
    setKeyword,
    handleSearch,
    canWrite,
    loading,
    error,
    page,
    totalPages,
    setPage,
  } = useDetectionEvents();

  return (
    <DetectionEventsListForm
      events={events}
      keyword={keyword}
      onKeywordChange={setKeyword}
      onSearch={handleSearch}
      canWrite={canWrite}
      loading={loading}
      error={error}
      page={page}
      totalPages={totalPages}
      onPageChange={setPage}
    />
  );
}

