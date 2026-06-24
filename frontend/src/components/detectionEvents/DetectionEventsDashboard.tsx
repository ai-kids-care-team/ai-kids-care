'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useSelector } from 'react-redux';
import type { RootState } from '@/store/index';
import {
  getDetectionEvents,
  DETECTION_EVENTS_RECENT_SIZE,
  type DetectionEventListItem,
} from '@/services/apis/detectionEvents.api';
import { useDetectionEventStream, type StreamStatus } from './useDetectionEventStream';

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

  const [events, setEvents] = useState<DetectionEventListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [highlighted, setHighlighted] = useState<Set<number>>(new Set());

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
                  <dd className="inline text-slate-700">{e.severity ?? '-'}</dd>
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
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
