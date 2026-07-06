'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useSelector } from 'react-redux';
import type { RootState } from '@/store/index';
import {
  getDetectionEvents,
  DETECTION_EVENTS_RECENT_SIZE,
  type DetectionEventListItem,
} from '@/services/apis/detectionEvents.api';
import {
  confirmEventReview,
  type EventStatusEnum,
  type ReviewResultStatus,
} from '@/services/apis/eventReviews.api';
import { useDetectionEventStream, type StreamStatus } from './useDetectionEventStream';
import { severityClasses, severityLevel } from '@/lib/severity';

const STATUS_BADGE: Record<string, string> = {
  OPEN: 'bg-red-100 text-red-700',
  ESCALATED: 'bg-red-200 text-red-800',
  ACKNOWLEDGED: 'bg-amber-100 text-amber-700',
  IN_REVIEW: 'bg-blue-100 text-blue-700',
  RESOLVED: 'bg-emerald-100 text-emerald-700',
  DISMISSED: 'bg-slate-100 text-slate-600',
};

const MAX_CARDS = 100;
const HIGHLIGHT_MS = 4000;

/** 카드에서 바로 확정 가능한 주요 처치(최소 집합). */
const REVIEW_ACTIONS: { status: ReviewResultStatus; label: string; className: string }[] = [
  { status: 'RESOLVED', label: '해결', className: 'bg-emerald-600 hover:bg-emerald-700' },
  { status: 'ESCALATED', label: '에스컬레이션', className: 'bg-red-600 hover:bg-red-700' },
  { status: 'DISMISSED', label: '기각', className: 'bg-slate-500 hover:bg-slate-600' },
];

/** 더 이상 동작을 노출하지 않는 종료 상태. */
const TERMINAL_STATUSES = new Set(['RESOLVED', 'DISMISSED']);

function formatTime(iso: string | null): string {
  if (!iso) return '-';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString('ko-KR');
}

function streamLabel(status: StreamStatus): { dot: string; text: string } {
  switch (status) {
    case 'open':
      return { dot: 'bg-emerald-500', text: '실시간 연결됨' };
    case 'error':
      return { dot: 'bg-amber-500', text: '재연결 중…' };
    default:
      return { dot: 'bg-slate-400', text: '연결 중…' };
  }
}

/**
 * ⑥ 실시간 감지 이벤트 대시보드. 진입 시 최근 이력을 불러오고(REST), 이후 SSE로 증분 수신합니다.
 * eventId로 중복 제거하며 새 이벤트는 잠시 강조됩니다. 테넌트/스태프 스코프는 백엔드가 강제하고,
 * active kindergarten이 바뀌면 이력 재조회 + 스트림을 다시 연결합니다.
 */
export function DetectionEventsDashboard() {
  const kindergartenId = useSelector((s: RootState) => s.user.user?.kindergartenId);
  const role = useSelector((s: RootState) => s.user.user?.role);
  // 후엔드가 권위 인증. 프론트 문지기는 UI 降噪용이며 stale 한 canResolveAnomaly 대신 role 直判.
  const canReview = role === 'KINDERGARTEN_ADMIN' || role === 'TEACHER';

  const [events, setEvents] = useState<DetectionEventListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [highlighted, setHighlighted] = useState<Set<number>>(new Set());
  // 카드별 코멘트 입력 / 제출 중 / 확정 오류(모두 onClick·onChange 콜백에서만 setState).
  const [comments, setComments] = useState<Record<number, string>>({});
  const [submitting, setSubmitting] = useState<Set<number>>(new Set());
  const [reviewErrors, setReviewErrors] = useState<Record<number, string>>({});

  // collected highlight timers, cleared on unmount to avoid dangling timeouts
  const timersRef = useRef<number[]>([]);
  useEffect(() => {
    const timers = timersRef.current;
    return () => timers.forEach((id) => window.clearTimeout(id));
  }, []);

  // initial / on-kindergarten-change history load (setState lives inside the async IIFE, not the
  // effect body, per react-hooks rules)
  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setLoadError(null);
      try {
        const page = await getDetectionEvents({ size: DETECTION_EVENTS_RECENT_SIZE });
        if (!cancelled) setEvents(page.content);
      } catch {
        if (!cancelled) setLoadError('감지 이벤트를 불러오지 못했습니다.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [kindergartenId]);

  const onLive = useCallback((incoming: DetectionEventListItem) => {
    setEvents((prev) => {
      if (prev.some((e) => e.eventId === incoming.eventId)) return prev; // de-dupe by id
      return [incoming, ...prev].slice(0, MAX_CARDS);
    });
    setHighlighted((prev) => new Set(prev).add(incoming.eventId));
    const id = window.setTimeout(() => {
      setHighlighted((prev) => {
        const next = new Set(prev);
        next.delete(incoming.eventId);
        return next;
      });
    }, HIGHLIGHT_MS);
    timersRef.current.push(id);
  }, []);

  // 카드 내 복合 확정. SSE 는 상태 변경을 다시 밀어주지 않으므로(신규 ingest 만 push)
  // 성공 시 본 카드 status 를 낙관적으로 갱신하고, 실패 시 직전 status 로 롤백 + 오류 노출.
  // 모든 setState 는 이 onClick 비동기 콜백 안에서만 호출된다(set-state-in-effect 회피).
  const handleReview = useCallback(
    async (eventId: number, resultStatus: ReviewResultStatus, prevStatus: EventStatusEnum | null) => {
      setReviewErrors((prev) => {
        if (!(eventId in prev)) return prev;
        const next = { ...prev };
        delete next[eventId];
        return next;
      });
      setSubmitting((prev) => new Set(prev).add(eventId));
      setEvents((prev) =>
        prev.map((e) => (e.eventId === eventId ? { ...e, status: resultStatus } : e)),
      );
      try {
        const comment = comments[eventId]?.trim();
        await confirmEventReview({
          eventId,
          resultStatus,
          ...(comment ? { comment } : {}),
        });
      } catch {
        // 롤백: 직전 status 로 되돌리고 오류 메시지 노출.
        setEvents((prev) =>
          prev.map((e) => (e.eventId === eventId ? { ...e, status: prevStatus } : e)),
        );
        setReviewErrors((prev) => ({ ...prev, [eventId]: '검토 확정에 실패했습니다. 다시 시도해 주세요.' }));
      } finally {
        setSubmitting((prev) => {
          const next = new Set(prev);
          next.delete(eventId);
          return next;
        });
      }
    },
    [comments],
  );

  // reconnect the SSE stream when the active kindergarten changes
  const streamStatus = useDetectionEventStream(onLive, { reconnectKey: kindergartenId });
  const live = streamLabel(streamStatus);

  return (
    <main className="min-h-screen bg-gray-50 px-4 py-8">
      <section className="mx-auto max-w-3xl">
        <header className="mb-6 flex items-center justify-between">
          <h1 className="text-xl font-semibold text-slate-900">실시간 이상행동 감지</h1>
          <span className="inline-flex items-center gap-2 text-sm text-slate-600">
            <span className={`h-2.5 w-2.5 rounded-full ${live.dot}`} aria-hidden />
            {live.text}
          </span>
        </header>

        {loading && <p className="text-sm text-slate-500">불러오는 중…</p>}
        {loadError && <p className="text-sm text-red-600">{loadError}</p>}
        {!loading && !loadError && events.length === 0 && (
          <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow">
            아직 감지된 이벤트가 없습니다. 새 이벤트는 실시간으로 표시됩니다.
          </p>
        )}

        <ul className="space-y-3">
          {events.map((e) => (
            <li
              key={e.eventId}
              className={`rounded-xl bg-white p-4 shadow transition ${
                highlighted.has(e.eventId) ? 'ring-2 ring-emerald-400' : ''
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="font-medium text-slate-900">{e.eventType ?? '알 수 없음'}</span>
                <span
                  className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                    STATUS_BADGE[e.status ?? ''] ?? 'bg-slate-100 text-slate-600'
                  }`}
                >
                  {e.status ?? '-'}
                </span>
              </div>
              <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-slate-500">
                <div>
                  <dt className="inline">감지 시각: </dt>
                  <dd className="inline text-slate-700">{formatTime(e.detectedAt ?? e.createdAt)}</dd>
                </div>
                <div>
                  <dt className="inline">심각도: </dt>
                  <dd className="inline text-slate-700">
                    {e.severity != null ? (
                      <span
                        className={`rounded border px-1.5 py-0.5 text-[11px] font-medium ${severityClasses(severityLevel(e.severity))}`}
                      >
                        {e.severity}
                      </span>
                    ) : (
                      '-'
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="inline">카메라: </dt>
                  <dd className="inline text-slate-700">{e.cameraName ?? e.cameraId ?? '-'}</dd>
                </div>
                <div>
                  <dt className="inline">교실: </dt>
                  <dd className="inline text-slate-700">{e.roomName ?? e.roomId ?? '-'}</dd>
                </div>
              </dl>

              {canReview && !TERMINAL_STATUSES.has(e.status ?? '') && (
                <div className="mt-3 border-t border-slate-100 pt-3">
                  <input
                    type="text"
                    value={comments[e.eventId] ?? ''}
                    onChange={(ev) =>
                      setComments((prev) => ({ ...prev, [e.eventId]: ev.target.value }))
                    }
                    placeholder="검토 코멘트(선택)"
                    disabled={submitting.has(e.eventId)}
                    className="mb-2 w-full rounded-md border border-slate-200 px-2.5 py-1.5 text-xs text-slate-700 placeholder:text-slate-400 focus:border-slate-400 focus:outline-none disabled:opacity-60"
                  />
                  <div className="flex flex-wrap gap-2">
                    {REVIEW_ACTIONS.map((action) => (
                      <button
                        key={action.status}
                        type="button"
                        disabled={submitting.has(e.eventId)}
                        onClick={() => handleReview(e.eventId, action.status, e.status)}
                        className={`rounded-md px-3 py-1.5 text-xs font-medium text-white transition disabled:opacity-60 ${action.className}`}
                      >
                        {action.label}
                      </button>
                    ))}
                  </div>
                  {reviewErrors[e.eventId] && (
                    <p className="mt-2 text-xs text-red-600">{reviewErrors[e.eventId]}</p>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
