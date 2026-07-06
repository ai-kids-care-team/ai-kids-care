'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  getDetectionEvents,
  DETECTION_EVENTS_RECENT_SIZE,
  type DetectionEventListItem,
} from '@/services/apis/detectionEvents.api';
import { useDetectionEventStream } from '@/components/detectionEvents/useDetectionEventStream';

/** 카드/타일 목록에 유지할 상한(신규 SSE 이벤트 prepend 후 이 값으로 자름). `DetectionEventsDashboard`의 MAX_CARDS와 동일 값. */
export const CCTV_ALERTS_MAX = 100;

const HIGHLIGHT_MS = 4000;

export type UseCctvAlertsResult = {
  events: DetectionEventListItem[];
  loading: boolean;
  highlightedEventIds: Set<number>;
};

/**
 * UX-02: CCTV 대시보드의 실시간 이상 알림. 진입 시 REST로 최근 이력을 불러오고(원본 `load()`의
 * 무조건적 `setEvents([])`을 대체), 이후 (공유) `useDetectionEventStream`으로 증분 수신한다
 * (`DetectionEventsDashboard.tsx`의 onLive 패턴 재사용 — eventId 로 중복 제거, prepend, 상한
 * 유지, 4초 하이라이트). `enabled=false`(live-stream 권한 없는 역할)면 REST 호출도, SSE 구독도
 * 하지 않는다 — 거부될 스트림을 애초에 열지 않는다(ai-detection spec 시나리오).
 */
export function useCctvAlerts(
  enabled: boolean,
  reconnectKey: number | string | null | undefined,
): UseCctvAlertsResult {
  const [events, setEvents] = useState<DetectionEventListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [highlightedEventIds, setHighlightedEventIds] = useState<Set<number>>(new Set());

  const timersRef = useRef<number[]>([]);
  useEffect(() => {
    const timers = timersRef.current;
    return () => timers.forEach((id) => window.clearTimeout(id));
  }, []);

  useEffect(() => {
    if (!enabled) {
      setEvents([]);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    void getDetectionEvents({ size: DETECTION_EVENTS_RECENT_SIZE })
      .then((page) => {
        if (!cancelled) setEvents(page.content);
      })
      .catch(() => {
        if (!cancelled) setEvents([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [enabled, reconnectKey]);

  const onLive = useCallback((incoming: DetectionEventListItem) => {
    setEvents((prev) => {
      if (prev.some((e) => e.eventId === incoming.eventId)) return prev; // eventId 로 중복 제거
      return [incoming, ...prev].slice(0, CCTV_ALERTS_MAX);
    });
    setHighlightedEventIds((prev) => new Set(prev).add(incoming.eventId));
    const id = window.setTimeout(() => {
      setHighlightedEventIds((prev) => {
        const next = new Set(prev);
        next.delete(incoming.eventId);
        return next;
      });
    }, HIGHLIGHT_MS);
    timersRef.current.push(id);
  }, []);

  useDetectionEventStream(onLive, { enabled, reconnectKey: reconnectKey ?? undefined });

  return { events, loading, highlightedEventIds };
}
