'use client';

import { useEffect, useRef, useState } from 'react';
import { API_BASE_URL } from '@/config/api';
import type { DetectionEventListItem } from '@/services/apis/detectionEvents.api';

export type StreamStatus = 'connecting' | 'open' | 'error';

/**
 * ⑥ 실시간 감지 이벤트 SSE 구독. 백엔드 `GET /api/v1/detection-events/stream`(세션 쿠키 인증,
 * 테넌트 스코프)에 EventSource로 연결하고 `detection-event` 이벤트마다 onEvent를 호출합니다.
 * EventSource는 끊기면 브라우저가 자동 재연결합니다. reconnectKey(예: active kindergartenId)가
 * 바뀌면 연결을 다시 맺습니다. 언마운트 시 정리합니다.
 */
export function useDetectionEventStream(
  onEvent: (event: DetectionEventListItem) => void,
  options: { enabled?: boolean; reconnectKey?: string | number } = {},
): StreamStatus {
  const { enabled = true, reconnectKey } = options;
  const onEventRef = useRef(onEvent);
  useEffect(() => {
    onEventRef.current = onEvent;
  }, [onEvent]);

  const [status, setStatus] = useState<StreamStatus>('connecting');

  useEffect(() => {
    if (!enabled || typeof window === 'undefined') {
      return;
    }
    // initial state is already 'connecting'; status transitions happen in the async EventSource
    // callbacks below (setting state directly in an effect body is disallowed by react-hooks rules).
    const url = `${API_BASE_URL}/detection-events/stream`;
    const source = new EventSource(url, { withCredentials: true });

    source.addEventListener('connected', () => setStatus('open'));
    source.addEventListener('detection-event', (ev) => {
      try {
        const data = JSON.parse((ev as MessageEvent).data) as DetectionEventListItem;
        onEventRef.current(data);
      } catch {
        // ignore malformed frame
      }
    });
    source.onopen = () => setStatus('open');
    source.onerror = () => {
      // EventSource auto-reconnects; surface the transient error state for the UI.
      setStatus('error');
    };

    return () => source.close();
  }, [enabled, reconnectKey]);

  return status;
}
