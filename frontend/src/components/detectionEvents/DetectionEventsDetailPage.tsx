'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { AlertTriangle, ClipboardList, CheckSquare, Search, CheckCircle2, XCircle, Megaphone } from 'lucide-react';
import { useDetectionEventDetail } from '@/components/detectionEvents/functions/useDetectionEventDetail';
import { EventReviewFlow } from '@/components/detectionEvents/EventReviewFlow';
import { createEventReview, getLatestEventReview, type CreateEventReviewDTO, type EventReview } from '@/services/apis/eventReviews.api';
import { updateDetectionEvent } from '@/services/apis/detectionEvents.api';
import { useAppSelector } from '@/store/hook';
import { getParentCommonCodeList, type CommonCode } from '@/services/apis/commonCodes.api';

function formatDateTime(value: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  const hours24 = date.getHours();
  const period = hours24 < 12 ? '오전' : '오후';
  const hours12Raw = hours24 % 12;
  const hours12 = hours12Raw === 0 ? 12 : hours12Raw;
  const mi = String(date.getMinutes()).padStart(2, '0');
  const ss = String(date.getSeconds()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd} (${period}) ${hours12}시 ${mi}분 ${ss}초`;
}

export function DetectionEventsDetailPage() {
  const { id, detail, loading, error, reload } = useDetectionEventDetail();
  const { user } = useAppSelector((state) => state.user);
  const [latestReview, setLatestReview] = useState<EventReview | null>(null);
  const [memo, setMemo] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);
  const [eventTypeLabel, setEventTypeLabel] = useState<string | null>(null);

  useEffect(() => {
    const loadLatest = async () => {
      if (!Number.isFinite(id) || id <= 0) return;
      try {
        const review = await getLatestEventReview(id);
        setLatestReview(review);
      } catch (e) {
        console.error('최신 처리 상태 조회 실패:', e);
      }
    };

    void loadLatest();
  }, [id]);

  // 사건 유형 코드 → 한글 명칭 매핑
  useEffect(() => {
    const loadEventTypeLabel = async () => {
      if (!detail?.eventType) {
        setEventTypeLabel(null);
        return;
      }
      try {
        const codes: CommonCode[] = await getParentCommonCodeList('detection_events', 'event_type');
        const normalized = detail.eventType.trim().toLowerCase();
        const matched = codes.find((c) => c.code.trim().toLowerCase() === normalized);
        const label = (matched?.codeName ?? matched?.code_name)?.trim();
        setEventTypeLabel(label || null);
      } catch (e) {
        console.error('사건 유형 공통코드 로딩 실패:', e);
        setEventTypeLabel(null);
      }
    };

    void loadEventTypeLabel();
  }, [detail?.eventType]);

  const latestStatus = latestReview?.result_status ?? null;
  const showConfirm = latestStatus === 'OPEN';
  const showReview = latestStatus === 'ACKNOWLEDGED';
  const showResolve = latestStatus === 'IN_REVIEW';
  const showDismiss =
    latestStatus === 'ACKNOWLEDGED' || latestStatus === 'IN_REVIEW';
  const showEscalate = latestStatus === 'RESOLVED';
  const hasAnyAction = showConfirm || showReview || showResolve || showDismiss || showEscalate;

  const handleStatusChange = async (
    nextStatus: 'ACKNOWLEDGED' | 'IN_REVIEW' | 'RESOLVED' | 'DISMISSED' | 'ESCALATED',
  ) => {
    if (!Number.isFinite(id) || id <= 0) return;
    if (!user || !user.id) {
      console.warn('로그인한 사용자 정보가 없습니다.');
      return;
    }

    const reviewDto: CreateEventReviewDTO = {
      event_id: id,
      user_id: user.id,
      from_status: latestStatus ?? null,
      result_status: nextStatus,
      comment: memo.trim() || null,
    };

    const detectionUpdateDto = {
      status: nextStatus,
    } as const;

    try {
      await Promise.all([
        createEventReview(reviewDto),
        updateDetectionEvent(id, detectionUpdateDto),
      ]);
      // 상세 정보 전체 재조회
      await reload();
      const refreshed = await getLatestEventReview(id);
      setLatestReview(refreshed);
      setRefreshKey((prev) => prev + 1);
    } catch (e) {
      console.error('상태 변경 처리 실패:', e);
    }
  };

  if (loading) {
    return <div className="min-h-screen bg-gray-50 p-6 text-center text-gray-500">불러오는 중입니다.</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-5xl">
        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-6 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <AlertTriangle className="h-7 w-7 text-[#d97706]" />
              <h2 className="text-2xl text-slate-900">이상 탐지 상세 정보</h2>
            </div>
            <Link
              href="/detectionEvents"
              className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-4 py-2 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-100 hover:border-slate-300"
            >
              <ClipboardList className="h-4 w-4 text-slate-600" />
              목록으로
            </Link>
          </div>

          {error && <p className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-600">{error}</p>}

          {!error && !detail && (
            <p className="rounded-lg bg-slate-50 p-4 text-sm text-slate-600">이벤트 정보를 찾을 수 없습니다.</p>
          )}

          {!error && detail && (
            <div className="space-y-10">
              {/* 처리 프로세스 플로우 */}
              <EventReviewFlow key={refreshKey} eventId={id} />

              {/* 기본 정보 */}
              <section>
                <h3 className="mb-4 text-lg font-semibold text-slate-900">기본 정보</h3>
                <div className="overflow-hidden rounded border border-slate-200">
                  <div className="grid grid-cols-4 text-sm">
                    {/* 1행 */}
                    <div className="border-b border-r border-slate-200 bg-slate-50 px-4 py-3 font-medium text-slate-700">
                      유치원명
                    </div>
                    <div className="border-b border-r border-slate-200 px-4 py-3 text-slate-800">
                      {detail.kindergartenName ?? '-'}
                    </div>
                    <div className="border-b border-r border-slate-200 bg-slate-50 px-4 py-3 font-medium text-slate-700">
                      교실명
                    </div>
                    <div className="border-b border-slate-200 px-4 py-3 text-slate-800">
                      {detail.roomName ?? '-'}
                    </div>

                    {/* 2행 */}
                    <div className="border-b border-r border-slate-200 bg-slate-50 px-4 py-3 font-medium text-slate-700">
                      사건 유형
                    </div>
                    <div className="border-b border-r border-slate-200 px-4 py-3 text-slate-800">
                      {eventTypeLabel ?? detail.eventType ?? '이벤트 유형 미지정'}
                    </div>
                    <div className="border-b border-r border-slate-200 bg-slate-50 px-4 py-3 font-medium text-slate-700">
                      탐지 발생 일시
                    </div>
                    <div className="border-b border-slate-200 px-4 py-3 text-slate-800">
                      {formatDateTime(detail.detectedAt)}
                    </div>

                    {/* 3행 */}
                    <div className="border-r border-slate-200 bg-slate-50 px-4 py-3 font-medium text-slate-700">
                      사건 시작 시간
                    </div>
                    <div className="border-r border-slate-200 px-4 py-3 text-slate-800">
                      {formatDateTime(detail.startTime)}
                    </div>
                    <div className="border-r border-slate-200 bg-slate-50 px-4 py-3 font-medium text-slate-700">
                      사건 종료 시간
                    </div>
                    <div className="px-4 py-3 text-slate-800">
                      {formatDateTime(detail.endTime)}
                    </div>
                  </div>
                </div>
              </section>

              {/* 위험도 분석 */}
              <section>
                <h3 className="mb-4 text-lg font-semibold text-slate-900">위험도 분석</h3>
                <div className="rounded-xl border border-[#cfe0ff] bg-[#f7fbff] p-5">
                  <div className="mb-3 flex items-center justify-between text-sm text-slate-700">
                    <span>
                      심각도 (Level {detail.severity ?? '-'}/10)
                    </span>
                  </div>
                  <div className="mb-1 flex gap-2">
                    {Array.from({ length: 10 }).map((_, idx) => {
                      const level = (detail.severity ?? 0) || 0;
                      const isCurrent = level > 0 && idx === level - 1;

                      // 피그마 팔레트: 빨강 3개, 연빨강 2개, 노랑 2개, 연초록 3개
                      const baseColors = [
                        '#f44336',
                        '#f97373',
                        '#fbb6b6',
                        '#ffe0e0',
                        '#ffecec',
                        '#ffe6a3',
                        '#ffefb3',
                        '#b8f5d0',
                        '#a8f0c5',
                        '#a0ebbf',
                      ];

                      const backgroundColor = baseColors[idx] ?? '#ffffff';

                      if (!isCurrent) {
                        return (
                          <span
                            // eslint-disable-next-line react/no-array-index-key
                            key={idx}
                            className="h-6 w-6 rounded-full border border-transparent shadow-sm"
                            style={{ backgroundColor }}
                          />
                        );
                      }

                      return (
                        <span
                          // eslint-disable-next-line react/no-array-index-key
                          key={idx}
                          className="relative flex h-6 w-6 items-center justify-center"
                        >
                          <span className="absolute inline-flex h-7 w-7 rounded-full bg-red-500 opacity-40 animate-ping" />
                          <span
                            className="relative h-6 w-6 rounded-full border border-transparent shadow-sm"
                            style={{ backgroundColor }}
                          />
                        </span>
                      );
                    })}
                  </div>

                  {detail.severity != null && detail.severity > 0 && (
                    <p className="mb-4 flex items-center gap-2 text-xs text-slate-700">
                      <span className="inline-block h-3 w-3 rounded-full bg-red-600" />
                      <span>긴급상황 - 즉시 대응 필요</span>
                    </p>
                  )}

                  <div className="mb-2 flex items-center justify-between text-sm text-slate-700">
                    <span>신뢰도</span>
                    <span className="text-xs text-emerald-700">확정 수준</span>
                  </div>
                  <div className="h-5 w-full overflow-hidden rounded-full bg-slate-200">
                    {(() => {
                      const percent =
                        detail.confidence == null
                          ? 0
                          : Math.round(Math.max(0, Math.min(1, detail.confidence)) * 100);
                      const barColorClass = percent > 80 ? 'bg-emerald-500' : 'bg-yellow-400';
                      return (
                        <div
                          className={`flex h-full items-center justify-end rounded-full pr-3 text-xs font-semibold text-white ${barColorClass}`}
                          style={{ width: `${percent}%` }}
                        >
                          {percent > 0 ? `${percent}%` : ''}
                        </div>
                      );
                    })()}
                  </div>
                </div>
              </section>

              {/* 처리 메모 */}
              <section>
                <h3 className="mb-3 text-lg font-semibold text-slate-900">처리 메모</h3>
                <textarea
                  className="h-32 w-full resize-none rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700"
                  placeholder="처리 내용이나 특이사항을 입력하세요..."
                  value={memo}
                  onChange={(e) => setMemo(e.target.value)}
                  readOnly={!hasAnyAction}
                />
              </section>

              {/* 상태 변경 (UI만, 동작 없음) */}
              <section>
                <div className="flex flex-wrap justify-center gap-3">
                  {showConfirm && (
                    <button
                      type="button"
                      className="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-5 py-2 text-sm text-slate-700 shadow-sm"
                      onClick={() => handleStatusChange('ACKNOWLEDGED')}
                    >
                      <CheckSquare className="h-4 w-4 text-emerald-600" />
                      <span>확인</span>
                    </button>
                  )}
                  {showReview && (
                    <button
                      type="button"
                      className="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-5 py-2 text-sm text-slate-700 shadow-sm"
                      onClick={() => handleStatusChange('IN_REVIEW')}
                    >
                      <Search className="h-4 w-4 text-slate-700" />
                      <span>검토</span>
                    </button>
                  )}
                  {showResolve && (
                    <button
                      type="button"
                      className="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-5 py-2 text-sm text-slate-700 shadow-sm"
                      onClick={() => handleStatusChange('RESOLVED')}
                    >
                      <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                      <span>완료</span>
                    </button>
                  )}
                  {showDismiss && (
                    <button
                      type="button"
                      className="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-5 py-2 text-sm text-slate-700 shadow-sm"
                      onClick={() => handleStatusChange('DISMISSED')}
                    >
                      <XCircle className="h-4 w-4 text-red-500" />
                      <span>오탐</span>
                    </button>
                  )}
                  {showEscalate && (
                    <button
                      type="button"
                      className="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-5 py-2 text-sm text-slate-700 shadow-sm"
                      onClick={() => handleStatusChange('ESCALATED')}
                    >
                      <Megaphone className="h-4 w-4 text-red-500" />
                      <span>보고</span>
                    </button>
                  )}
                </div>
              </section>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

