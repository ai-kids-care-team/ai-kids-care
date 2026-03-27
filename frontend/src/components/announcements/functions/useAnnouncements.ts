'use client';

import { useEffect, useMemo, useState } from 'react';
import { ANNOUNCEMENTS_LIST_PAGE_SIZE, getAnnouncements } from '@/services/apis/announcements.api';
import { useAppSelector } from '@/store/hook';
import { canManageAnnouncements } from '@/types/user-role';
import { getAnnouncementBaseDate, isAnnouncementNew, sortAnnouncementsByNewPriority } from './announcement-sort';

import {AnnouncementItem} from '@/types/announcement';

function formatDate(value: string) {
  const date = new Date(value);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd}`;
}

export function useAnnouncements() {
  const { user, token, isAuthenticated } = useAppSelector((state) => state.user);
  const [announcements, setAnnouncements] = useState<AnnouncementItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const canWrite = useMemo(
    () =>
      Boolean(isAuthenticated && user && token && canManageAnnouncements(user.role)),
    [isAuthenticated, user, token],
  );

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const pageData = await getAnnouncements({
          keyword: appliedKeyword || undefined,
          page,
          size: ANNOUNCEMENTS_LIST_PAGE_SIZE,
          sort: ['status,asc', 'isPinned,desc', 'publishedAt,desc'],
        });
        const now = Date.now();
        const sortedContent = sortAnnouncementsByNewPriority(pageData.content, now);
        setTotalPages(pageData.totalPages);
        setAnnouncements(
          sortedContent.map((item) => {
            const baseDate = getAnnouncementBaseDate(item);
            if (!baseDate) {
              return {
                id: item.id,
                title: item.title,
                date: '-',
                isNew: false,
                views: item.viewCount ?? 0,
                href: `/announcements/read?id=${item.id}`,
              };
            }
            return {
              id: item.id,
              title: item.title,
              date: formatDate(baseDate),
              isNew: isAnnouncementNew(item, now),
              views: item.viewCount ?? 0,
              href: `/announcements/read?id=${item.id}`,
            };
          }),
        );
      } catch (e) {
        console.error('공지사항 목록 조회 실패:', e);
        setError('공지사항 목록을 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [appliedKeyword, page]);

  const handleSearch = () => {
    setAppliedKeyword(keyword.trim());
    setPage(0);
  };

  return {
    announcements,
    keyword,
    setKeyword,
    handleSearch,
    canWrite,
    loading,
    error,
    page,
    totalPages,
    setPage,
  };
}
