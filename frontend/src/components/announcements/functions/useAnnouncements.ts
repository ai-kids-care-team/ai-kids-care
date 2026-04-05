'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  ANNOUNCEMENTS_DEFAULT_SORT,
  ANNOUNCEMENTS_LIST_PAGE_SIZE,
  getAnnouncements,
} from '@/services/apis/announcements.api';
import { useAppSelector } from '@/store/hook';
import { canManageAnnouncements } from '@/types/user-role';
import { getAnnouncementBaseDate, isAnnouncementNew, sortAnnouncementsByNewPriority } from './announcement-sort';

import {AnnouncementItem} from '@/types/announcement';

const ANNOUNCEMENTS_FETCH_BATCH_SIZE = 100;

function formatDate(value: string) {
  const date = new Date(value);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd}`;
}

async function getAllAnnouncements(keyword?: string) {
  const firstPage = await getAnnouncements({
    keyword,
    page: 0,
    size: ANNOUNCEMENTS_FETCH_BATCH_SIZE,
    sort: [...ANNOUNCEMENTS_DEFAULT_SORT],
  });

  if (firstPage.totalPages <= 1) {
    return firstPage.content;
  }

  const remainingPages = await Promise.all(
    Array.from({ length: firstPage.totalPages - 1 }, (_, index) =>
      getAnnouncements({
        keyword,
        page: index + 1,
        size: ANNOUNCEMENTS_FETCH_BATCH_SIZE,
        sort: [...ANNOUNCEMENTS_DEFAULT_SORT],
      }),
    ),
  );

  return [firstPage, ...remainingPages].flatMap((pageData) => pageData.content);
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
        const allContent = await getAllAnnouncements(appliedKeyword || undefined);
        const now = Date.now();
        const sortedContent = sortAnnouncementsByNewPriority(allContent, now);
        const totalPageCount = Math.ceil(sortedContent.length / ANNOUNCEMENTS_LIST_PAGE_SIZE);
        const pagedContent = sortedContent.slice(
          page * ANNOUNCEMENTS_LIST_PAGE_SIZE,
          (page + 1) * ANNOUNCEMENTS_LIST_PAGE_SIZE,
        );

        setTotalPages(totalPageCount);
        setAnnouncements(
          pagedContent.map((item) => {
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
