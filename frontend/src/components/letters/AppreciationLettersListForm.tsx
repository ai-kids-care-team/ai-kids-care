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

  const LIST_MIN_HEIGHT = 'min-h-[600px]';
  const useInnerScroll = !loading && items.length > 4;

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-5xl">
        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-8 flex items-center justify-between border-b border-gray-200 pb-6">
            <div className="flex items-center gap-3">
              <Heart className="h-7 w-7 text-[#006b52]" />
              <h2 className="text-3xl">감사 편지</h2>
            </div>
            {hydrated && canWrite && (
              <Link
                href="/letters/write"
                className="flex items-center gap-2 rounded-lg bg-[#006b52] px-5 py-2.5 text-white transition-colors hover:bg-[#005640]"
              >
                <Plus className="h-5 w-5" />
                글쓰기
              </Link>
            )}
          </div>

          <div className="mb-6 flex items-center gap-2">
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
            className={[LIST_MIN_HEIGHT, 'space-y-3', useInnerScroll ? 'max-h-[600px] overflow-y-auto pr-1' : '']
              .filter(Boolean)
              .join(' ')}
          >
            {loading ? (
              <p className="flex min-h-[520px] items-center justify-center text-center text-gray-500">
                감사 편지를 불러오는 중입니다.
              </p>
            ) : items.length === 0 ? (
              <p className="flex min-h-[520px] items-center justify-center text-center text-gray-500">
                등록된 감사 편지가 없습니다.
              </p>
            ) : (
              items.map((item) => (
                item.href ? (
                  <Link
                    key={item.key}
                    href={item.href}
                    className="block rounded-lg border border-gray-200 p-5 transition-all hover:border-emerald-300 hover:bg-emerald-50"
                  >
                    <div className="flex items-start gap-3">
                      <div className="min-w-0 flex-1">
                        <p className="text-lg transition-colors hover:text-[#006b52]">{item.title}</p>
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
                    className="block rounded-lg border border-gray-200 p-5 opacity-70"
                  >
                    <div className="flex items-start gap-3">
                      <div className="min-w-0 flex-1">
                        <p className="text-lg">{item.title}</p>
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
            <div className="mt-8 flex min-h-[3.25rem] flex-wrap items-center justify-center gap-3 border-t border-gray-100 pt-6">
              <button
                type="button"
                disabled={page <= 0}
                onClick={() => onPageChange(page - 1)}
                className="rounded-lg border border-gray-300 px-4 py-2 text-sm text-slate-700 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                이전
              </button>
              <span className="text-sm text-gray-600">
                {page + 1} / {totalPages}
              </span>
              <button
                type="button"
                disabled={page >= totalPages - 1}
                onClick={() => onPageChange(page + 1)}
                className="rounded-lg border border-gray-300 px-4 py-2 text-sm text-slate-700 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                다음
              </button>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
