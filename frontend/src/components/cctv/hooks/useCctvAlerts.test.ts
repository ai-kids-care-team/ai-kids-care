import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { DetectionEventListItem } from '@/services/apis/detectionEvents.api';

const getDetectionEventsMock = vi.fn();
let capturedOnEvent: ((event: DetectionEventListItem) => void) | null = null;
let capturedOptions: { enabled?: boolean; reconnectKey?: string | number } | undefined;

vi.mock('@/services/apis/detectionEvents.api', () => ({
  DETECTION_EVENTS_RECENT_SIZE: 20,
  getDetectionEvents: (...args: unknown[]) => getDetectionEventsMock(...args),
}));

vi.mock('@/components/detectionEvents/useDetectionEventStream', () => ({
  useDetectionEventStream: (
    onEvent: (event: DetectionEventListItem) => void,
    options?: { enabled?: boolean; reconnectKey?: string | number },
  ) => {
    capturedOnEvent = onEvent;
    capturedOptions = options;
    return 'connecting';
  },
}));

const { useCctvAlerts, CCTV_ALERTS_MAX } = await import('./useCctvAlerts');

function makeEvent(overrides: Partial<DetectionEventListItem> = {}): DetectionEventListItem {
  return {
    eventId: 1,
    kindergartenId: 1,
    kindergartenName: null,
    cameraId: 1,
    cameraName: null,
    roomId: null,
    roomName: null,
    sessionId: null,
    eventType: 'ASSAULT',
    severity: 8,
    confidence: 90,
    detectedAt: '2026-07-06T12:00:00+09:00',
    startTime: null,
    endTime: null,
    status: 'OPEN',
    createdAt: null,
    updatedAt: null,
    ...overrides,
  };
}

/**
 * cctv-dashboard-refactor-alerts (C5) / UX-02: 이 hook은 CCTV 대시보드가 REST로 최근 알림을
 * 불러오고, 이후 (공유) `useDetectionEventStream`으로 증분 수신하는 로직을 캡슐화한다.
 * 여기서는 원본이 요구한 3가지 계약 — eventId 중복 제거, 상한(MAX_CARDS) 유지,
 * enabled=false일 때 REST/SSE 모두 건너뜀 — 을 검증한다.
 */
describe('useCctvAlerts', () => {
  beforeEach(() => {
    capturedOnEvent = null;
    capturedOptions = undefined;
    getDetectionEventsMock.mockReset();
    getDetectionEventsMock.mockResolvedValue({
      content: [makeEvent({ eventId: 1 })],
      totalElements: 1,
      totalPages: 1,
      size: 20,
      number: 0,
      first: true,
      last: true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads recent alerts via REST on mount when enabled', async () => {
    const { result } = renderHook(() => useCctvAlerts(true, 1));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(getDetectionEventsMock).toHaveBeenCalledTimes(1);
    expect(result.current.events).toHaveLength(1);
    expect(result.current.events[0].eventId).toBe(1);
  });

  it('does not call the REST endpoint or open the SSE stream when disabled', async () => {
    const { result } = renderHook(() => useCctvAlerts(false, 1));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(getDetectionEventsMock).not.toHaveBeenCalled();
    expect(result.current.events).toEqual([]);
    expect(capturedOptions?.enabled).toBe(false);
  });

  it('de-duplicates incoming SSE events by eventId and prepends new ones', async () => {
    const { result } = renderHook(() => useCctvAlerts(true, 1));
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      capturedOnEvent?.(makeEvent({ eventId: 2 }));
    });
    expect(result.current.events.map((e) => e.eventId)).toEqual([2, 1]);

    // duplicate arrival (same eventId) must not add a second card
    act(() => {
      capturedOnEvent?.(makeEvent({ eventId: 2 }));
    });
    expect(result.current.events.map((e) => e.eventId)).toEqual([2, 1]);
  });

  it('caps the event list at CCTV_ALERTS_MAX after prepending', async () => {
    getDetectionEventsMock.mockResolvedValue({
      content: Array.from({ length: CCTV_ALERTS_MAX }, (_, i) => makeEvent({ eventId: i + 1 })),
      totalElements: CCTV_ALERTS_MAX,
      totalPages: 1,
      size: CCTV_ALERTS_MAX,
      number: 0,
      first: true,
      last: true,
    });
    const { result } = renderHook(() => useCctvAlerts(true, 1));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.events).toHaveLength(CCTV_ALERTS_MAX);

    act(() => {
      capturedOnEvent?.(makeEvent({ eventId: CCTV_ALERTS_MAX + 100 }));
    });
    expect(result.current.events).toHaveLength(CCTV_ALERTS_MAX);
    expect(result.current.events[0].eventId).toBe(CCTV_ALERTS_MAX + 100);
  });

  it('marks a newly-arrived event as highlighted, then clears the highlight after 4s', async () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useCctvAlerts(true, 1));
    await vi.waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      capturedOnEvent?.(makeEvent({ eventId: 99 }));
    });
    expect(result.current.highlightedEventIds.has(99)).toBe(true);

    act(() => {
      vi.advanceTimersByTime(4000);
    });
    expect(result.current.highlightedEventIds.has(99)).toBe(false);
  });
});
