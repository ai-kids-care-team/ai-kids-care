'use client';

import { useEffect, useState } from 'react';
import {
  ANNOUNCEMENTS_LIST_PAGE_SIZE,
  getAnnouncements,
  getAnnouncementsMeta,
} from '@/services/apis/announcements.api';
import { useAppSelector } from '@/store/hook';

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
        const pageData = await getAnnouncements({
          keyword: appliedKeyword || undefined,
          page,
          size: ANNOUNCEMENTS_LIST_PAGE_SIZE,
        });
        const now = Date.now();
        setTotalPages(pageData.totalPages);
        setAnnouncements(
          pageData.content.map((item) => {
            const baseDate = item.publishedAt ?? item.createdAt;
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
            const isNew = now - new Date(baseDate).getTime() <= 7 * 24 * 60 * 60 * 1000;
            return {
              id: item.id,
              title: item.title,
              date: formatDate(baseDate),
              isNew,
              views: item.viewCount ?? 0,
              href: `/announcements/read?id=${item.id}`,
            };
          }),
        );

        // 목록 조회는 비로그인도 가능하므로, 쓰기 권한 조회 실패가 목록 자체를 막지 않게 분리한다.
        if (isAuthenticated && user && token) {
          try {
            const meta = await getAnnouncementsMeta();
            setCanWrite(meta.canWrite);
          } catch {
            setCanWrite(false);
          }
        } else {
          setCanWrite(false);
        }
      } catch (e) {
        console.error('공지사항 목록 조회 실패:', e);
        setError('공지사항 목록을 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [appliedKeyword, page, user, token, isAuthenticated]);

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
