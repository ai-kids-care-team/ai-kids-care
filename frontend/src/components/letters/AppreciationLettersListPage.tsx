'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  APPRECIATION_LETTERS_PAGE_SIZE,
  getAppreciationLetters,
} from '@/services/apis/appreciationLetters.api';
import {
  formatLetterDate,
  isAppreciationLetterPublic,
  isSameAppreciationLetterAuthor,
  letterStatusLabel,
  resolveAppreciationLetterId,
} from './appreciation-letter-utils';
import { AppreciationLettersListForm, type AppreciationLetterListItem } from './AppreciationLettersListForm';
import type { AppreciationLetterVO } from '@/types/appreciationLetter';
import { useAppSelector } from '@/store/hook';
import { canWriteAppreciationLetters } from '@/types/user-role';
import { listClientCachedLetters } from './appreciation-letter-client-cache';
import { getApiErrorMessage } from './api-error-message';

function rowSenderUserIdNum(row: unknown): number {
  const r = row as Record<string, unknown>;
  const n = Number(r.senderUserId ?? r.sender_user_id);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

/** 백엔드 리스트 응답에는 `letterId`가 null일 수 있으므로 href는 없을 수 있음 */
function mapRowsToListItems(
  rows: AppreciationLetterVO[],
  opts?: { cacheSeqBySignature?: Map<string, number> },
): AppreciationLetterListItem[] {
  return rows.flatMap((row, rowIndex) => {
    const letterId = resolveAppreciationLetterId(row as unknown as Record<string, unknown>);

    // 캐시 매칭/서버 해석용 시그니처(정규화)
    const signature = `${row.title}|${Number(row.senderUserId)}|${String(row.targetType ?? '').toUpperCase()}|${Number(row.targetId)}`;
    const cachedSeq = opts?.cacheSeqBySignature?.get(signature);
    const href =
      letterId != null
        ? `/letters/read?id=${letterId}`
        : cachedSeq != null
          ? `/letters/read?cid=${cachedSeq}`
          : `/letters/read?sig=${encodeURIComponent(
              JSON.stringify({
                title: row.title,
                senderUserId: Number(row.senderUserId),
                targetType: String(row.targetType ?? '').toUpperCase(),
                targetId: Number(row.targetId),
              }),
            )}`;

    const key =
      letterId != null
        ? `api-${letterId}-r${rowIndex}`
        : cachedSeq != null
          ? `api-cache-${cachedSeq}-r${rowIndex}`
          : `api-sig-${Number(row.senderUserId)}-${String(row.targetType ?? '').toUpperCase()}-${Number(row.targetId)}-${row.createdAt}-r${rowIndex}`;

    return [
      {
        key,
        title: row.title,
        date: formatLetterDate(row.createdAt),
        statusLabel: letterStatusLabel(row.status),
        href,
        isPublic: isAppreciationLetterPublic(row as AppreciationLetterVO & Record<string, unknown>),
        senderUserId: rowSenderUserIdNum(row),
      },
    ];
  });
}

function filterListForViewer(
  list: AppreciationLetterListItem[],
  viewer: { id: string } | null,
  isAuthenticated: boolean,
): AppreciationLetterListItem[] {
  return list.filter((it) => {
    if (it.isPublic !== false) return true;
    if (!isAuthenticated || !viewer) return false;
    return (
      it.senderUserId != null &&
      it.senderUserId > 0 &&
      isSameAppreciationLetterAuthor(viewer.id, it.senderUserId)
    );
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

  const visibleItems = useMemo(
    () => filterListForViewer(items, user, isAuthenticated),
    [items, user, isAuthenticated],
  );

  useEffect(() => {
    let cancelled = false;

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
        if (cancelled) return;

        const cachedItems: AppreciationLetterListItem[] =
          page === 0
            ? listClientCachedLetters().map(({ seq, vo }) => ({
                key: `cache-${seq}`,
                title: vo.title,
                date: formatLetterDate(vo.createdAt),
                statusLabel: letterStatusLabel(vo.status),
                href: `/letters/read?cid=${seq}`,
                isPublic: vo.isPublic,
                senderUserId: vo.senderUserId,
              }))
            : [];

        const cacheSeqBySignature =
          page === 0
            ? new Map(
                listClientCachedLetters().map(({ seq, vo }) => [
                  `${vo.title}|${vo.senderUserId}|${String(vo.targetType ?? '').toUpperCase()}|${vo.targetId}`,
                  seq,
                ]),
              )
            : undefined;

        const totalEl = pageData.totalElements ?? 0;
        const rows = pageData.content ?? [];
        setTotalPages(Math.max(pageData.totalPages ?? (totalEl > 0 ? 1 : 1), 1));
        const apiItems = mapRowsToListItems(rows, { cacheSeqBySignature });
        const apiItemsDeduped =
          page === 0
            ? apiItems.filter((it) => !(it.href?.startsWith('/letters/read?cid=')))
            : apiItems;
        setItems(page === 0 ? [...cachedItems, ...apiItemsDeduped] : [...cachedItems, ...apiItems]);
      } catch (e) {
        if (cancelled) return;
        console.warn('감사 편지 목록 조회 실패:', e);
        const cachedItems: AppreciationLetterListItem[] =
          page === 0
            ? listClientCachedLetters().map(({ seq, vo }) => ({
                key: `cache-${seq}`,
                title: vo.title,
                date: formatLetterDate(vo.createdAt),
                statusLabel: letterStatusLabel(vo.status),
                href: `/letters/read?cid=${seq}`,
                isPublic: vo.isPublic,
                senderUserId: vo.senderUserId,
              }))
            : [];
        setTotalPages(1);
        setItems(cachedItems);
        setError(getApiErrorMessage(e, '목록을 불러오지 못했습니다.'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [appliedKeyword, page]);

  const handleSearch = () => {
    setPage(0);
    setAppliedKeyword(keyword.trim());
  };

  return (
    <AppreciationLettersListForm
      items={visibleItems}
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
