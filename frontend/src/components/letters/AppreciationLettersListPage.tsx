'use client';

import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { APPRECIATION_LETTERS_PAGE_SIZE, getAppreciationLetters } from '@/services/apis/appreciationLetters.api';
import {
  formatLetterDate,
} from './appreciation-letter-utils';
import { AppreciationLettersListForm, type AppreciationLetterListItem } from './AppreciationLettersListForm';
import type { AppreciationLetterVO } from '@/types/appreciationLetter';
import { useAppSelector } from '@/store/hook';
import { canWriteAppreciationLetters } from '@/types/user-role';
import { getApiErrorMessage } from './api-error-message';

function mapRowsToListItems(
  rows: AppreciationLetterVO[],
): AppreciationLetterListItem[] {
  return rows.map((row, rowIndex) => ({
    key: `api-${row.letterId}-r${rowIndex}`,
    title: row.title,
    date: formatLetterDate(row.createdAt),
    href: `/letters/read?id=${row.letterId}`,
  }));
}

export function AppreciationLettersListPage() {
  const { user, isAuthenticated } = useAppSelector((state) => state.user);
  const searchParams = useSearchParams();
  const reloadToken = searchParams.get('reload');
  const [items, setItems] = useState<AppreciationLetterListItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const canWrite = useMemo(
    () =>
      Boolean(
        isAuthenticated && user && canWriteAppreciationLetters(user.role),
      ),
    [isAuthenticated, user],
  );

  const safePage = useMemo(
    () => Math.min(page, totalPages > 0 ? totalPages - 1 : 0),
    [page, totalPages],
  );

  // 목록 진입 시 항상 최신(1페이지)부터 보여주기
  useEffect(() => {
    setPage(0);
  }, [reloadToken]);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const pageData = await getAppreciationLetters({
          keyword: appliedKeyword || undefined,
          page: safePage,
          size: APPRECIATION_LETTERS_PAGE_SIZE,
          sort: 'createdAt,desc',
        });
        if (cancelled) return;

        const rows = pageData.content ?? [];
        const apiItems = mapRowsToListItems(rows);
        setItems(apiItems);
        setTotalPages(Math.max(1, pageData.totalPages));
      } catch (e) {
        if (cancelled) return;
        console.warn('감사 편지 목록 조회 실패:', e);
        setItems([]);
        setTotalPages(1);
        setError(getApiErrorMessage(e, '목록을 불러오지 못했습니다.'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [appliedKeyword, reloadToken, safePage]);

  const handleSearch = () => {
    setPage(0);
    setAppliedKeyword(keyword.trim());
  };

  return (
    <AppreciationLettersListForm
      items={items}
      keyword={keyword}
      onKeywordChange={setKeyword}
      onSearch={handleSearch}
      canWrite={canWrite}
      loading={loading}
      error={error}
      page={safePage}
      totalPages={totalPages}
      onPageChange={setPage}
    />
  );
}
