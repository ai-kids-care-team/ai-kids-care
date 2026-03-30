'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  APPRECIATION_LETTERS_PAGE_SIZE,
  getAppreciationLetters,
} from '@/services/apis/appreciationLetters.api';
import { formatLetterDate, letterStatusLabel, resolveAppreciationLetterId } from '@/lib/appreciation-letter-utils';
import { AppreciationLettersListForm, type AppreciationLetterListItem } from './AppreciationLettersListForm';
import type { AppreciationLetterVO } from '@/types/appreciationLetter';
import {
  DUMMY_APPRECIATION_LETTERS,
  getDummyAppreciationLettersPage,
} from '@/lib/dummy-data/appreciationLetters';
import { useAppSelector } from '@/store/hook';
import { canWriteAppreciationLetters } from '@/types/user-role';

/** 현재 API 페이지에 온 글 ID와 중복되지 않는 더미만, 최신순 */
function dummiesBelowApi(
  apiRows: { letterId?: number | null; id?: number | null }[],
  keyword: string,
): AppreciationLetterVO[] {
  const apiIds = new Set(
    apiRows.flatMap((r) => {
      const id = resolveAppreciationLetterId(r);
      return id != null ? [id] : [];
    }),
  );
  const q = keyword.trim().toLowerCase();
  return [...DUMMY_APPRECIATION_LETTERS]
    .filter((d) => !apiIds.has(d.letterId))
    .filter((d) => {
      if (!q) return true;
      return (
        d.title.toLowerCase().includes(q) ||
        d.content.toLowerCase().includes(q) ||
        String(d.letterId).includes(q)
      );
    })
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
}

function mapRowsToListItems(
  rows: { letterId?: number | null; id?: number | null; title: string; createdAt: string; status: string }[],
): AppreciationLetterListItem[] {
  return rows.flatMap((row) => {
    const letterId = resolveAppreciationLetterId(row);
    if (letterId == null) {
      console.warn('감사 편지 목록 행에 유효한 ID가 없어 건너뜁니다.', row);
      return [];
    }
    return [
      {
        letterId,
        title: row.title,
        date: formatLetterDate(row.createdAt),
        statusLabel: letterStatusLabel(row.status),
        href: `/letters/read?id=${letterId}`,
      },
    ];
  });
}

export function AppreciationLettersListPage() {
  const { user, token, isAuthenticated } = useAppSelector((state) => state.user);
  const [items, setItems] = useState<AppreciationLetterListItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const canWrite = useMemo(
    () =>
      Boolean(
        isAuthenticated && user && token && canWriteAppreciationLetters(user.role),
      ),
    [isAuthenticated, user, token],
  );

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const pageData = await getAppreciationLetters({
          keyword: appliedKeyword || undefined,
          page,
          size: APPRECIATION_LETTERS_PAGE_SIZE,
          sort: 'createdAt,desc',
        });
        const totalEl = pageData.totalElements ?? 0;
        const rows = pageData.content ?? [];
        /* DB에 한 건도 없으면 시드 없이도 목록이 비지 않게 lib 더미로 채움 */
        if (totalEl === 0 && rows.length === 0) {
          const fallback = getDummyAppreciationLettersPage({
            keyword: appliedKeyword || undefined,
            page,
            size: APPRECIATION_LETTERS_PAGE_SIZE,
          });
          setTotalPages(fallback.totalPages);
          setItems(mapRowsToListItems(fallback.content));
        } else {
          setTotalPages(Math.max(pageData.totalPages ?? 1, 1));
          const apiItems = mapRowsToListItems(rows);
          /* 1페이지: 실제 글 아래 데모 글 이어 붙임(DB에 없는 letterId만, 검색어는 더미에도 적용) */
          if (page === 0) {
            const tail = dummiesBelowApi(rows, appliedKeyword);
            setItems([...apiItems, ...mapRowsToListItems(tail)]);
          } else {
            setItems(apiItems);
          }
        }
      } catch (e) {
        console.warn('감사 편지 목록 조회 실패 — 오프라인 데모 목록을 표시합니다.', e);
        const fallback = getDummyAppreciationLettersPage({
          keyword: appliedKeyword || undefined,
          page,
          size: APPRECIATION_LETTERS_PAGE_SIZE,
        });
        setTotalPages(fallback.totalPages);
        setItems(mapRowsToListItems(fallback.content));
        setError('');
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [appliedKeyword, page]);

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
      page={page}
      totalPages={totalPages}
      onPageChange={setPage}
    />
  );
}
