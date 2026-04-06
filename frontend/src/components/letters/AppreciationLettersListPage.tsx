'use client';

import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { APPRECIATION_LETTERS_PAGE_SIZE, getAppreciationLetters } from '@/services/apis/appreciationLetters.api';
import {
  buildAppreciationLetterViewerContext,
  buildAppreciationLetterVisibilityProbe,
  formatLetterDate,
  isAppreciationLetterPublic,
  letterStatusLabel,
  resolveAppreciationLetterId,
  resolveLetterKindergartenId,
  viewerMaySeeAppreciationLetter,
  type AppreciationLetterViewerContext,
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

    const kg = resolveLetterKindergartenId(row as unknown as Record<string, unknown>);

    return [
      {
        key,
        title: row.title,
        date: formatLetterDate(row.createdAt),
        statusLabel: letterStatusLabel(row.status),
        href,
        isPublic: isAppreciationLetterPublic(row as AppreciationLetterVO & Record<string, unknown>),
        senderUserId: rowSenderUserIdNum(row),
        kindergartenId: kg ?? undefined,
        dedupeSignature: signature,
      },
    ];
  });
}

function filterListForViewer(
  list: AppreciationLetterListItem[],
  viewerCtx: AppreciationLetterViewerContext,
  isAuthenticated: boolean,
): AppreciationLetterListItem[] {
  return list.filter((it) =>
    viewerMaySeeAppreciationLetter(
      buildAppreciationLetterVisibilityProbe({
        isPublic: it.isPublic,
        senderUserId: it.senderUserId ?? 0,
        kindergartenId: it.kindergartenId,
      }),
      viewerCtx,
      isAuthenticated,
    ),
  );
}

export function AppreciationLettersListPage() {
  const { user, token, isAuthenticated } = useAppSelector((state) => state.user);
  const searchParams = useSearchParams();
  const reloadToken = searchParams.get('reload');
  const [items, setItems] = useState<AppreciationLetterListItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const canWrite = useMemo(
    () =>
      Boolean(
        isAuthenticated && user && token && canWriteAppreciationLetters(user.role),
      ),
    [isAuthenticated, user, token],
  );

  const viewerCtx = useMemo(
    () => buildAppreciationLetterViewerContext(user, token),
    [user, token],
  );

  const allVisibleItems = useMemo(
    () => filterListForViewer(items, viewerCtx, isAuthenticated),
    [items, viewerCtx, isAuthenticated],
  );

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil(allVisibleItems.length / APPRECIATION_LETTERS_PAGE_SIZE)),
    [allVisibleItems.length],
  );

  const safePage = useMemo(
    () => Math.min(page, totalPages > 0 ? totalPages - 1 : 0),
    [page, totalPages],
  );

  const visibleItems = useMemo(
    () => {
      if (allVisibleItems.length === 0) return [];
      const start = safePage * APPRECIATION_LETTERS_PAGE_SIZE;
      const end = start + APPRECIATION_LETTERS_PAGE_SIZE;
      return allVisibleItems.slice(start, end);
    },
    [allVisibleItems, safePage],
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
          page: 0,
          size: undefined,
          sort: 'createdAt,desc',
        });
        if (cancelled) return;

        const cachedItems: AppreciationLetterListItem[] = listClientCachedLetters().map(({ seq, vo }) => {
          const signature = `${vo.title}|${Number(vo.senderUserId)}|${String(vo.targetType ?? '').toUpperCase()}|${Number(vo.targetId)}`;
          return {
            key: `cache-${seq}`,
            title: vo.title,
            date: formatLetterDate(vo.createdAt),
            statusLabel: letterStatusLabel(vo.status),
            href: `/letters/read?cid=${seq}`,
            isPublic: vo.isPublic,
            senderUserId: vo.senderUserId,
            kindergartenId: resolveLetterKindergartenId(vo as unknown as Record<string, unknown>) ?? undefined,
            dedupeSignature: signature,
          };
        });

        const cacheSeqBySignature = new Map(
          listClientCachedLetters().map(({ seq, vo }) => [
            `${vo.title}|${vo.senderUserId}|${String(vo.targetType ?? '').toUpperCase()}|${vo.targetId}`,
            seq,
          ]),
        );

        const rows = pageData.content ?? [];
        const apiItems = mapRowsToListItems(rows, { cacheSeqBySignature });
        const cachedSigs = new Set(
          cachedItems.map((it) => it.dedupeSignature).filter((s): s is string => Boolean(s)),
        );
        const apiItemsDeduped = apiItems.filter((it) => {
          const sig = it.dedupeSignature;
          return !sig || !cachedSigs.has(sig);
        });
        setItems([...cachedItems, ...apiItemsDeduped]);
      } catch (e) {
        if (cancelled) return;
        console.warn('감사 편지 목록 조회 실패:', e);
        const cachedItems: AppreciationLetterListItem[] = listClientCachedLetters().map(({ seq, vo }) => {
          const signature = `${vo.title}|${Number(vo.senderUserId)}|${String(vo.targetType ?? '').toUpperCase()}|${Number(vo.targetId)}`;
          return {
            key: `cache-${seq}`,
            title: vo.title,
            date: formatLetterDate(vo.createdAt),
            statusLabel: letterStatusLabel(vo.status),
            href: `/letters/read?cid=${seq}`,
            isPublic: vo.isPublic,
            senderUserId: vo.senderUserId,
            kindergartenId: resolveLetterKindergartenId(vo as unknown as Record<string, unknown>) ?? undefined,
            dedupeSignature: signature,
          };
        });
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
  }, [appliedKeyword, reloadToken]);

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
      page={safePage}
      totalPages={totalPages}
      onPageChange={setPage}
    />
  );
}
