'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { AlertTriangle, Plus, Search } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';
import { toast } from 'sonner';
import type { DetectionEventItem } from '@/types/detectionEvents';

type DetectionEventsListFormProps = {
  events: DetectionEventItem[];
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

const toDateSafely = (value: string | null): Date | null => {
  if (!value) return null;

  const base = value.trim();
  const dotFormatMatch = base.match(
    /^(\d{4})\.(\d{1,2})\.(\d{1,2})\s+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?$/,
  );
  if (dotFormatMatch) {
    const [, year, month, day, hour, minute, second = '0'] = dotFormatMatch;
    const date = new Date(
      Number(year),
      Number(month) - 1,
      Number(day),
      Number(hour),
      Number(minute),
      Number(second),
    );
    if (!Number.isNaN(date.getTime())) return date;
  }

  const normalized = base.includes('T') ? base : base.replace(' ', 'T');
  const candidates = [
    normalized,
    normalized.replace(/\./g, '-'),
    // DB datetime with microseconds(6자리)도 JS Date가 읽을 수 있게 3자리로 축소
    normalized.replace(/(\.\d{3})\d+/, '$1'),
    normalized.replace(/\./g, '-').replace(/(\.\d{3})\d+/, '$1'),
    // 타임존 표기가 없으면 로컬 기준으로 파싱
    `${normalized}Z`,
    `${normalized.replace(/\./g, '-')}Z`,
    `${normalized.replace(/(\.\d{3})\d+/, '$1')}Z`,
    `${normalized.replace(/\./g, '-').replace(/(\.\d{3})\d+/, '$1')}Z`,
  ];

  for (const candidate of candidates) {
    const date = new Date(candidate);
    if (!Number.isNaN(date.getTime())) return date;
  }

  return null;
};

const formatElapsedFromStart = (
  startTime: string | null,
  detectedAt: string | null,
  nowMs: number,
): string | null => {
  const startDate = toDateSafely(startTime) ?? toDateSafely(detectedAt);
  if (!startDate) return null;

  const diffMs = nowMs - startDate.getTime();
  if (diffMs < 0) return '0시간 경과';

  const totalHours = Math.floor(diffMs / (1000 * 60 * 60));
  const days = Math.floor(totalHours / 24);
  const hours = totalHours % 24;

  if (days > 0) return `${days}일 ${hours}시간 경과`;
  return `${hours}시간 경과`;
};

export function DetectionEventsListForm({
  events,
  keyword,
  onKeywordChange,
  onSearch,
  canWrite,
  loading,
  error,
  page,
  totalPages,
  onPageChange,
}: DetectionEventsListFormProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [mounted, setMounted] = useState(false);
  const [nowMs, setNowMs] = useState(() => Date.now());

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    const scrollY = sessionStorage.getItem('detectionEvents:list:scrollY');
    if (scrollY) {
      window.scrollTo({ top: Number(scrollY), behavior: 'auto' });
      sessionStorage.removeItem('detectionEvents:list:scrollY');
    }
  }, []);

  useEffect(() => {
    const resolved = searchParams.get('resolved');
    if (resolved === '1') {
      toast.success('이벤트가 처리되었습니다.');
      router.replace('/detectionEvents', { scroll: false });
    }
  }, [router, searchParams]);

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setNowMs(Date.now());
    }, 60_000);

    return () => window.clearInterval(intervalId);
  }, []);

  const rememberScroll = () => {
    sessionStorage.setItem('detectionEvents:list:scrollY', String(window.scrollY));
  };

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto w-[30%] max-w-5xl">
        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-4 flex items-center justify-between border-b border-gray-200 pb-3">
            <div className="flex items-center gap-3">
              <AlertTriangle className="h-6 w-6 text-[#d97706]" aria-hidden />
              <h2 className="text-2xl">이상 탐지</h2>
            </div>
          </div>

          <div className="mb-3 flex items-center gap-2">
            <input
              type="text"
              value={keyword}
              onChange={(e) => onKeywordChange(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') onSearch();
              }}
              placeholder="이벤트 유형, 상태 등으로 검색"
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

          <div className="origin-top" style={{ zoom: 0.7 }}>
            <div className="space-y-3">
            {loading ? (
              <p className="flex min-h-[520px] items-center justify-center text-center text-gray-500">
                탐지 이벤트를 불러오는 중입니다.
              </p>
            ) : events.length === 0 ? (
              <p className="flex min-h-[520px] items-center justify-center text-center text-gray-500">
                등록된 탐지 이벤트가 없습니다.
              </p>
            ) : (
              events.map((event) => (
                <Link
                  key={event.id}
                  href={event.href}
                  onClick={rememberScroll}
                  className="block rounded-lg border border-gray-200 p-5 transition-all hover:border-emerald-300 hover:bg-emerald-50"
                >
                  {(() => {
                    const confidence = event.confidence;
                    const percentRaw = confidence == null ? 0 : Math.max(0, Math.min(1, confidence)) * 100;
                    const percent = Math.round(percentRaw);
                    const severity = event.severity;
                    const elapsedLabel = formatElapsedFromStart(event.startTime, event.detectedAt, nowMs);
                    const severityBadgeStyle =
                      severity == null
                        ? ''
                        : severity >= 1 && severity <= 4
                          ? 'bg-[#ff0000] text-white'
                          : severity >= 5 && severity <= 7
                            ? 'bg-[#f3b300] text-white'
                            : 'bg-emerald-50 text-emerald-700';
                    const severityLabel =
                      severity == null
                        ? ''
                        : severity >= 1 && severity <= 4
                          ? '긴급'
                          : severity >= 5 && severity <= 7
                            ? '주의'
                            : '경미';

                    return (
                      <>
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-3">
                              <p className="text-lg transition-colors hover:text-[#006b52]">
                                {event.eventTypeName || '이벤트 유형 미지정'}
                              </p>
                              {severity != null && (
                                <span className={`rounded-full px-3 py-1 text-xs ${severityBadgeStyle}`}>
                                  {severityLabel} {severity}
                                </span>
                              )}
                              {event.statusName && (
                                <span className="rounded-sm border border-[#f3d27a] bg-[#fff9d6] px-2 py-0.5 text-[11px] text-[#b8860b]">
                                  {event.statusName}
                                </span>
                              )}
                              {elapsedLabel && (
                                <span className="text-xs font-semibold text-red-600">{elapsedLabel}</span>
                              )}
                            </div>
                            <div className="mt-2 flex flex-wrap items-center gap-3 text-sm text-gray-500">
                              <span>
                                {(event.kindergartenName || '-') + ' | ' + (event.roomName || '-')}
                              </span>
                              <span className="w-full">탐지 시각: {event.detectedAt}</span>
                              <span className="w-full">
                                사건시간: {event.startTime} ~ {event.endTime}
                              </span>
                            </div>
                          </div>

                          <div className="flex items-center gap-3">
                            <span className="text-sm font-semibold text-gray-900">
                              신뢰도 {confidence == null ? '-' : `${percent}%`}
                            </span>
                          </div>
                        </div>

                        {/* Figma 스타일: 카드 하단 1줄 진행바(신뢰도) */}
                        <div className="mt-4 h-2 w-full rounded-full bg-gray-100">
                          <div
                            className="h-2 rounded-full bg-[#006b52]"
                            style={{ width: `${confidence == null ? 0 : percent}%` }}
                          />
                        </div>
                      </>
                    );
                  })()}
                </Link>
              ))
            )}
            </div>
          </div>

          {!loading && totalPages > 1 && (
            <div className="mt-4 flex min-h-[1.625rem] flex-wrap items-center justify-center gap-3 border-t border-gray-100 pt-3">
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

