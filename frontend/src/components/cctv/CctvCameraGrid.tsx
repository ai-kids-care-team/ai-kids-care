'use client';

import { useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/shared/ui/button';
import type { CctvCameraVO } from '@/types/cctv.vo';
import type { DetectionEventListItem } from '@/services/apis/detectionEvents.api';
import type { CctvCategoryKey } from '@/lib/cctvFormat';
import { CctvCameraTile } from '@/components/cctv/CctvCameraTile';

export type CctvCameraGridProps = {
  categoryFilter: CctvCategoryKey;
  filteredCameras: CctvCameraVO[];
  displayGridSlots: (CctvCameraVO | null)[];
  eventsByCamera: Map<number, DetectionEventListItem[]>;
  safePage: number;
  itemsPerPage: number;
  totalPages: number;
  gridCols: string;
  canViewLiveStreams: boolean;
  isVideoPaused: boolean;
  onOpenDetail: (camera: CctvCameraVO) => void;
  onOpenFullscreenAt: (index: number) => void;
  onChangePage: (updater: (prev: number) => number) => void;
};

/** 카메라 그리드 + 페이지네이션(원본 CctvDashboardPage :778-1038). */
export function CctvCameraGrid({
  categoryFilter,
  filteredCameras,
  displayGridSlots,
  eventsByCamera,
  safePage,
  itemsPerPage,
  totalPages,
  gridCols,
  canViewLiveStreams,
  isVideoPaused,
  onOpenDetail,
  onOpenFullscreenAt,
  onChangePage,
}: CctvCameraGridProps) {
  // 카메라 타일 하단 이상 알림: 기본 3건만 표시, 초과 시 +로 전체 펼침(카메라별 UI 상태).
  const [tileAlertsExpandedByCamera, setTileAlertsExpandedByCamera] = useState<Record<number, boolean>>({});
  const toggleTileAlertsExpanded = (cameraId: number) => {
    setTileAlertsExpandedByCamera((prev) => ({ ...prev, [cameraId]: !prev[cameraId] }));
  };

  if (categoryFilter !== 'all' && filteredCameras.length === 0) {
    return (
      <div className="flex flex-col gap-4">
        <div className="flex min-h-[200px] items-center justify-center rounded-lg border border-dashed border-gray-300 bg-white px-4 text-center text-sm text-gray-500">
          이 구역에 해당하는 카메라가 없습니다.
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className={`grid w-full items-start gap-3 ${gridCols}`}>
        {displayGridSlots.map((camera, slotIdx) => {
          const gridSlot = safePage * itemsPerPage + slotIdx;
          const camEvents = camera ? (eventsByCamera.get(camera.cameraId) ?? []) : [];
          return (
            <CctvCameraTile
              key={camera != null ? camera.cameraId : `pad-${safePage}-${slotIdx}`}
              camera={camera}
              gridSlot={gridSlot}
              canViewLiveStreams={canViewLiveStreams}
              isVideoPaused={isVideoPaused}
              events={camEvents}
              tileAlertsExpanded={camera != null && Boolean(tileAlertsExpandedByCamera[camera.cameraId])}
              onToggleTileAlertsExpanded={toggleTileAlertsExpanded}
              onOpenDetail={onOpenDetail}
              onOpenFullscreen={(c) => {
                const idx = filteredCameras.findIndex((fc) => fc.cameraId === c.cameraId);
                if (idx < 0) return;
                onOpenFullscreenAt(idx);
              }}
            />
          );
        })}
      </div>

      {totalPages > 1 && (
        <div className="flex shrink-0 items-center justify-center gap-4 border-t border-gray-200 py-3">
          <Button
            size="sm"
            variant="outline"
            disabled={safePage === 0}
            onClick={() => onChangePage((p) => Math.max(0, p - 1))}
            type="button"
          >
            <ChevronLeft className="h-4 w-4" />
            이전
          </Button>
          <div className="flex items-center gap-2 text-sm text-gray-600">
            페이지 <span className="font-semibold text-gray-900">{safePage + 1}</span> / {totalPages}
            <span className="text-gray-400">•</span>
            총 {filteredCameras.length}대
          </div>
          <Button
            size="sm"
            variant="outline"
            disabled={safePage >= totalPages - 1}
            onClick={() => onChangePage((p) => Math.min(totalPages - 1, p + 1))}
            type="button"
          >
            다음
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}
    </div>
  );
}
