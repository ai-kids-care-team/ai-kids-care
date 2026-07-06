'use client';

import { useMemo } from 'react';
import type { DetectionEventListItem } from '@/services/apis/detectionEvents.api';
import type { EventStatusEnum } from '@/services/apis/eventReviews.api';
import type { CctvCameraVO } from '@/types/cctv.vo';
import { inferCategoryFromCameraName } from '@/lib/cctvFormat';
import type { CctvCategoryKey } from '@/lib/cctvFormat';

export type LayoutMode = '1x1' | '2x2' | '3x3';

export function mapCameraLineStatus(
  status: CctvCameraVO['status'],
): 'online' | 'offline' | 'maintenance' {
  switch (status) {
    case 'ACTIVE':
      return 'online';
    case 'PENDING':
      return 'maintenance';
    case 'DISABLED':
    default:
      return 'offline';
  }
}

/** 감지 이벤트(nullable status 허용) → 화면 상태 3분류. */
export function mapEventUiStatus(
  status: EventStatusEnum | null | undefined,
): 'active' | 'reviewing' | 'resolved' {
  switch (status) {
    case 'OPEN':
    case 'ESCALATED':
      return 'active';
    case 'ACKNOWLEDGED':
    case 'IN_REVIEW':
      return 'reviewing';
    case 'RESOLVED':
    case 'DISMISSED':
    default:
      return 'resolved';
  }
}

/** 타일에 최신순 전부(상한만) — 화면에는 3건까지, 나머지는 + 펼침 */
const MAX_TILE_EVENTS_PER_CAMERA = 120;

export type UseCctvGridDataParams = {
  cameras: CctvCameraVO[];
  events: DetectionEventListItem[];
  sessionKindergartenId: number | null;
  shouldScopeToOwnKindergarten: boolean;
  layout: LayoutMode;
  categoryFilter: CctvCategoryKey;
  currentPage: number;
  focusedCameraId: number | null;
};

/**
 * `CctvDashboardPage` 원본의 대량 파생 `useMemo` 블록(:359-477)을 통합한 hook.
 * cameras/events(테넌트·구역·페이지 스코프), 그리드 슬롯, 카메라별 이벤트 그룹, 통계를
 * 계산한다. 순수 파생 로직만 다루며 부수효과는 없다.
 */
export function useCctvGridData(params: UseCctvGridDataParams) {
  const {
    cameras,
    events,
    sessionKindergartenId,
    shouldScopeToOwnKindergarten,
    layout,
    categoryFilter,
    currentPage,
    focusedCameraId,
  } = params;

  const scopedCameras = useMemo(
    () =>
      shouldScopeToOwnKindergarten && sessionKindergartenId != null
        ? cameras.filter((c) => c.kindergartenId === sessionKindergartenId)
        : cameras,
    [cameras, sessionKindergartenId, shouldScopeToOwnKindergarten],
  );

  const filteredCameras = useMemo(() => {
    let base = scopedCameras;
    if (categoryFilter !== 'all') {
      base = base.filter((c) => inferCategoryFromCameraName(c.cameraName) === categoryFilter);
    }
    if (focusedCameraId != null) {
      const single = base.filter((c) => c.cameraId === focusedCameraId);
      if (single.length > 0) return single;
    }
    return base;
  }, [scopedCameras, categoryFilter, focusedCameraId]);

  const scopedEvents = useMemo(() => {
    if (shouldScopeToOwnKindergarten && sessionKindergartenId != null) {
      return events.filter((e) => e.kindergartenId === sessionKindergartenId);
    }
    return events;
  }, [events, sessionKindergartenId, shouldScopeToOwnKindergarten]);

  const itemsPerPage = layout === '1x1' ? 1 : layout === '2x2' ? 4 : 9;
  const totalPages = Math.max(1, Math.ceil(filteredCameras.length / itemsPerPage));
  const safePage = Math.min(currentPage, totalPages - 1);
  const pageCameras = filteredCameras.slice(
    safePage * itemsPerPage,
    safePage * itemsPerPage + itemsPerPage,
  );

  /** 전체: 레이아웃만큼 null 패드. 구역 필터: 해당 카메라만, 패드 없음 */
  const displayGridSlots = useMemo((): (CctvCameraVO | null)[] => {
    if (categoryFilter !== 'all') {
      return [...pageCameras];
    }
    const slots: (CctvCameraVO | null)[] = [...pageCameras];
    // 마지막 페이지에서는 불필요한 패드가 추가되어 카메라 타일 수가 늘어나는 문제 방지
    while (slots.length < itemsPerPage && safePage !== totalPages - 1) {
      slots.push(null);
    }
    return slots;
  }, [categoryFilter, pageCameras, itemsPerPage, safePage, totalPages]);

  const eventsByCamera = useMemo(() => {
    const sorted = [...scopedEvents].sort((a, b) => {
      const bt = b.detectedAt ? new Date(b.detectedAt).getTime() : 0;
      const at = a.detectedAt ? new Date(a.detectedAt).getTime() : 0;
      return bt - at;
    });
    const m = new Map<number, DetectionEventListItem[]>();
    for (const ev of sorted) {
      if (ev.cameraId == null) continue;
      const list = m.get(ev.cameraId) ?? [];
      if (list.length >= MAX_TILE_EVENTS_PER_CAMERA) continue;
      list.push(ev);
      m.set(ev.cameraId, list);
    }
    return m;
  }, [scopedEvents]);

  const activeAlertCount = useMemo(
    () => scopedEvents.filter((e) => mapEventUiStatus(e.status) === 'active').length,
    [scopedEvents],
  );

  const cameraStats = useMemo(() => {
    const total = scopedCameras.length;
    const online = scopedCameras.filter((c) => mapCameraLineStatus(c.status) === 'online').length;
    return { total, online, offline: Math.max(0, total - online) };
  }, [scopedCameras]);

  const categoryCounts = useMemo(() => {
    const counts: Record<Exclude<CctvCategoryKey, 'all'>, number> = {
      classroom: 0,
      corridor: 0,
      playground: 0,
      dining: 0,
      entrance: 0,
      hall: 0,
      security: 0,
    };
    for (const c of scopedCameras) {
      counts[inferCategoryFromCameraName(c.cameraName)] += 1;
    }
    return counts;
  }, [scopedCameras]);

  const gridCols = layout === '1x1' ? 'grid-cols-1' : layout === '2x2' ? 'grid-cols-2' : 'grid-cols-3';

  return {
    scopedCameras,
    filteredCameras,
    scopedEvents,
    itemsPerPage,
    totalPages,
    safePage,
    pageCameras,
    displayGridSlots,
    eventsByCamera,
    activeAlertCount,
    cameraStats,
    categoryCounts,
    gridCols,
  };
}
