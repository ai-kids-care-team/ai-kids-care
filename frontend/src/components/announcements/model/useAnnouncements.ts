'use client';

import { useEffect, useState } from 'react';
import { getAnnouncements, getAnnouncementsMeta } from '@/services/apis/announcements.api';

export type AnnouncementItem = {
  id: number;
  title: string;
  date: string;
  isNew: boolean;
  views: number;
  href: string;
};

function formatDate(value: string) {
  const date = new Date(value);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd}`;
}

export function useAnnouncements() {
  const [announcements, setAnnouncements] = useState<AnnouncementItem[]>([]);
  const [canWrite, setCanWrite] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const [list, meta] = await Promise.all([getAnnouncements(), getAnnouncementsMeta()]);
        const now = Date.now();
        setAnnouncements(
          list.map((item) => {
            const baseDate = item.publishedAt ?? item.createdAt;
            const isNew = now - new Date(baseDate).getTime() <= 7 * 24 * 60 * 60 * 1000;
            return {
              id: item.id,
              title: item.title,
              date: formatDate(baseDate),
              isNew,
              views: item.viewCount,
              href: `/announcements/read?id=${item.id}`,
            };
          }),
        );
        setCanWrite(meta.canWrite);
      } catch (e) {
        console.error('공지사항 목록 조회 실패:', e);
        setError('공지사항 목록을 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, []);

  return {
    announcements,
    canWrite,
    loading,
    error,
  };
}
