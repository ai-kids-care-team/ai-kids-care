import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CctvTileAlertList } from './CctvTileAlertList';
import type { DetectionEventListItem } from '@/services/apis/detectionEvents.api';

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
    confidence: 92,
    detectedAt: new Date().toISOString(),
    startTime: null,
    endTime: null,
    status: 'OPEN',
    createdAt: null,
    updatedAt: null,
    ...overrides,
  };
}

/**
 * cctv-dashboard-refactor-alerts (C5) task 4.4: 심각도 배지가 공유 `lib/severity.ts` 분류
 * 경계(≥7=high/≥4=medium/그 외=low)에 맞는 배지 스타일로 렌더되는지 검증한다. 이 컴포넌트가
 * CCTV 대시보드에서 실제 SSE 이벤트를 표시하는 타일 알림 목록이다.
 */
describe('CctvTileAlertList severity badge rendering', () => {
  it('renders a high-severity event with the red severity classes', () => {
    render(
      <CctvTileAlertList events={[makeEvent({ severity: 9 })]} expanded={false} onToggleExpand={vi.fn()} />,
    );
    const card = screen.getByText('ASSAULT').closest('div.rounded-md');
    expect(card).toHaveClass('bg-red-100', 'text-red-700', 'border-red-300');
  });

  it('renders a medium-severity event with the orange severity classes', () => {
    render(
      <CctvTileAlertList events={[makeEvent({ severity: 5 })]} expanded={false} onToggleExpand={vi.fn()} />,
    );
    const card = screen.getByText('ASSAULT').closest('div.rounded-md');
    expect(card).toHaveClass('bg-orange-100', 'text-orange-700', 'border-orange-300');
  });

  it('renders a low-severity event with the yellow severity classes', () => {
    render(
      <CctvTileAlertList events={[makeEvent({ severity: 1 })]} expanded={false} onToggleExpand={vi.fn()} />,
    );
    const card = screen.getByText('ASSAULT').closest('div.rounded-md');
    expect(card).toHaveClass('bg-yellow-100', 'text-yellow-700', 'border-yellow-300');
  });

  it('shows a "no alerts" placeholder when the event list is empty', () => {
    render(<CctvTileAlertList events={[]} expanded={false} onToggleExpand={vi.fn()} />);
    expect(screen.getByText('이상 없음')).toBeInTheDocument();
  });

  it('caps visible cards at 3 and exposes a "show more" toggle when collapsed', () => {
    const events = Array.from({ length: 5 }, (_, i) =>
      makeEvent({ eventId: i + 1, severity: 8 }),
    );
    render(<CctvTileAlertList events={events} expanded={false} onToggleExpand={vi.fn()} />);
    expect(screen.getAllByText('ASSAULT')).toHaveLength(3);
    expect(screen.getByLabelText('이상 알림 2건 더 보기')).toBeInTheDocument();
  });

  it('shows every card when expanded, with a collapse control instead', () => {
    const events = Array.from({ length: 5 }, (_, i) =>
      makeEvent({ eventId: i + 1, severity: 8 }),
    );
    render(<CctvTileAlertList events={events} expanded onToggleExpand={vi.fn()} />);
    expect(screen.getAllByText('ASSAULT')).toHaveLength(5);
    expect(screen.getByText('접기')).toBeInTheDocument();
  });
});
