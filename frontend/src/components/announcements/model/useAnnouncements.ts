'use client';

import { useEffect, useState } from 'react';
import {
  ANNOUNCEMENTS_LIST_PAGE_SIZE,
  getAnnouncements,
  getAnnouncementsMeta,
} from '@/services/apis/announcements.api';
import { useAppSelector } from '@/store/hook';

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
  const { user, token, isAuthenticated } = useAppSelector((state) => state.user);
  const [announcements, setAnnouncements] = useState<AnnouncementItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [canWrite, setCanWrite] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    // 로그아웃 직후에는 메타 재조회 응답을 기다리지 않고 즉시 숨긴다.
    if (!isAuthenticated || !user || !token) {
      setCanWrite(false);
    }
  }, [isAuthenticated, user, token]);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const [pageData, meta] = await Promise.all([
          getAnnouncements({
            keyword: appliedKeyword || undefined,
            page,
            size: ANNOUNCEMENTS_LIST_PAGE_SIZE,
          }),
          getAnnouncementsMeta(),
        ]);
        const now = Date.now();
        setTotalPages(pageData.totalPages);
        setAnnouncements(
          pageData.content.map((item) => {
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
  }, [appliedKeyword, page, user?.id, token, isAuthenticated]);

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
