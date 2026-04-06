'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { Heart, Plus, Search } from 'lucide-react';

export type AppreciationLetterListItem = {
  /** React key·중복 방지 (예: api-16-r0, demo-16-r0) */
  key: string;
  title: string;
  date: string;
  statusLabel: string;
  /** 백엔드 응답에서 letterId가 null이면 링크를 만들 수 없어서 optional 처리 */
  href?: string;
  /** false면 비공개 — 프론트에서 작성자 본인만 목록에 표시 */
  isPublic?: boolean;
  senderUserId?: number;
  /** 유치원 스코프 필터용 (`AppreciationLetterVO.kindergartenId`) */
  kindergartenId?: number;

  /** 캐시/서버 중복 제거용 (title/sender/target 조합) */
  dedupeSignature?: string;
};

type AppreciationLettersListFormProps = {
  items: AppreciationLetterListItem[];
  keyword: string;
  onKeywordChange: (value: string) => void;
  onSearch: () => void;
  canWrite: boolean;
  loading: boolean;
  error: string;
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
};

/** 0-based `page`, `totalPages` — 번호 버튼 + 생략 구간용 */
function paginationSlots(page: number, totalPages: number): (number | 'gap')[] {
  if (totalPages <= 1) return [];
  if (totalPages <= 9) {
    return Array.from({ length: totalPages }, (_, i) => i);
  }
  const want = new Set<number>([
    0,
    totalPages - 1,
    page,
    page - 1,
    page + 1,
  ].filter((i) => i >= 0 && i < totalPages));
  const sorted = [...want].sort((a, b) => a - b);
  const out: (number | 'gap')[] = [];
  let prev = -2;
  for (const i of sorted) {
    if (prev >= 0 && i - prev > 1) out.push('gap');
    out.push(i);
    prev = i;
  }
  return out;
}

export function AppreciationLettersListForm({
  items,
  keyword,
  onKeywordChange,
  onSearch,
  canWrite,
  loading,
  error,
  page,
  totalPages,
  onPageChange,
}: AppreciationLettersListFormProps) {
  const [hydrated, setHydrated] = useState(false);
  useEffect(() => {
    setHydrated(true);
  }, []);

  const LIST_MIN_HEIGHT = 'min-h-[480px]';
  const useInnerScroll = !loading && items.length > 6;
  const slots = paginationSlots(page, totalPages);

  return (
    <div className="bg-gray-50 px-4 py-4 sm:px-5 sm:py-5">
      <main className="mx-auto max-w-[51.2rem]">
        <div className="rounded-2xl bg-white p-6 shadow-lg">
          <div className="mb-6 flex items-center justify-between border-b border-gray-200 pb-5">
            <div className="flex items-center gap-2.5">
              <Heart className="h-6 w-6 text-[#006b52]" />
              <h2 className="text-2xl font-semibold tracking-tight">감사 편지</h2>
            </div>
            {hydrated && canWrite && (
              <Link
                href="/letters/write"
                className="flex items-center gap-1.5 rounded-lg bg-[#006b52] px-4 py-2 text-sm text-white transition-colors hover:bg-[#005640]"
              >
                <Plus className="h-4 w-4" />
                글쓰기
              </Link>
            )}
          </div>

          <div className="mb-5 flex items-center gap-2">
            <input
              type="text"
              value={keyword}
              onChange={(e) => onKeywordChange(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') onSearch();
              }}
              placeholder="제목 또는 내용으로 검색"
              className="w-full rounded-lg border border-gray-300 px-4 py-2 text-sm text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
            />
            <button
              type="button"
              onClick={onSearch}
              className="inline-flex shrink-0 items-center gap-1 rounded-lg bg-[#006b52] px-4 py-2 text-sm text-white transition-colors hover:bg-[#005640]"
            >
              <Search className="h-4 w-4" />
              검색
            </button>
          </div>

          {error && <p className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-600">{error}</p>}

          <div
            className={[LIST_MIN_HEIGHT, 'space-y-2.5', useInnerScroll ? 'max-h-[480px] overflow-y-auto pr-1' : '']
              .filter(Boolean)
              .join(' ')}
          >
            {loading ? (
              <p className="flex min-h-[416px] items-center justify-center text-center text-sm text-gray-500">
                감사 편지를 불러오는 중입니다.
              </p>
            ) : items.length === 0 ? (
              <p className="flex min-h-[416px] items-center justify-center text-center text-sm text-gray-500">
                등록된 감사 편지가 없습니다.
              </p>
            ) : (
              items.map((item) => (
                item.href ? (
                  <Link
                    key={item.key}
                    href={item.href}
                    className="block rounded-lg border border-gray-200 p-4 transition-all hover:border-emerald-300 hover:bg-emerald-50"
                  >
                    <div className="flex items-start gap-2.5">
                      <div className="min-w-0 flex-1">
                        <p className="text-base font-medium transition-colors hover:text-[#006b52]">{item.title}</p>
                        <div className="mt-2 flex flex-wrap items-center gap-4 text-sm text-gray-500">
                          <span>{item.date}</span>
                          <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-700">
                            {item.statusLabel}
                          </span>
                        </div>
                      </div>
                    </div>
                  </Link>
                ) : (
                  <div
                    key={item.key}
                    className="block rounded-lg border border-gray-200 p-4 opacity-70"
                  >
                    <div className="flex items-start gap-2.5">
                      <div className="min-w-0 flex-1">
                        <p className="text-base font-medium">{item.title}</p>
                        <div className="mt-2 flex flex-wrap items-center gap-4 text-sm text-gray-500">
                          <span>{item.date}</span>
                          <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-700">
                            {item.statusLabel}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                )
              ))
            )}
          </div>

          {!loading && totalPages > 1 && (
            <nav
              className="mt-6 flex min-h-[2.75rem] flex-wrap items-center justify-center gap-2 border-t border-gray-100 pt-5"
              aria-label="감사 편지 목록 페이지"
            >
              <button
                type="button"
                disabled={page <= 0}
                onClick={() => onPageChange(page - 1)}
                className="rounded-lg border border-gray-300 px-3 py-2 text-sm text-slate-700 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                이전
              </button>
              <div className="flex flex-wrap items-center justify-center gap-1">
                {slots.map((slot, idx) =>
                  slot === 'gap' ? (
                    <span key={`gap-${idx}`} className="px-1.5 text-sm text-gray-400" aria-hidden>
                      …
                    </span>
                  ) : (
                    <button
                      key={slot}
                      type="button"
                      onClick={() => onPageChange(slot)}
                      aria-current={slot === page ? 'page' : undefined}
                      className={
                        slot === page
                          ? 'min-w-[2.25rem] rounded-lg bg-[#006b52] px-2.5 py-2 text-sm font-medium text-white'
                          : 'min-w-[2.25rem] rounded-lg border border-gray-200 px-2.5 py-2 text-sm text-slate-700 transition-colors hover:bg-gray-50'
                      }
                    >
                      {slot + 1}
                    </button>
                  ),
                )}
              </div>
              <button
                type="button"
                disabled={page >= totalPages - 1}
                onClick={() => onPageChange(page + 1)}
                className="rounded-lg border border-gray-300 px-3 py-2 text-sm text-slate-700 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                다음
              </button>
            </nav>
          )}
        </div>
      </main>
    </div>
  );
}
