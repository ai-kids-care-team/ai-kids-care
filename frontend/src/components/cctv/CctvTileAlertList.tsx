'use client';

import { AlertTriangle, Plus } from 'lucide-react';
import { Badge } from '@/components/shared/ui/badge';
import { Button } from '@/components/shared/ui/button';
import type { DetectionEventListItem } from '@/services/apis/detectionEvents.api';
import { severityClasses, severityLevel } from '@/lib/severity';
import { formatRelativeMinutes } from '@/lib/cctvFormat';
import { mapEventUiStatus } from '@/components/cctv/hooks/useCctvGridData';

export type CctvTileAlertListProps = {
  events: DetectionEventListItem[];
  expanded: boolean;
  onToggleExpand: () => void;
};

/** 카메라 타일 하단 이상 알림 — 기본 3건만 표시, 초과 시 +로 전체 펼침(원본 :914-1001). */
export function CctvTileAlertList({ events, expanded, onToggleExpand }: CctvTileAlertListProps) {
  const hasMore = events.length > 3;
  const visibleEvents = events.length > 0 ? (expanded ? events : events.slice(0, 3)) : [];

  return (
    <div
      className={`border-t border-gray-100 bg-gray-50 ${
        events.length > 0
          ? `${expanded ? 'max-h-72' : 'max-h-32'} min-h-0 shrink-0 overflow-y-auto p-2`
          : 'shrink-0 px-3 py-2'
      }`}
    >
      {events.length > 0 ? (
        <div className="space-y-1.5">
          {visibleEvents.map((event) => {
            const level = severityLevel(event.severity ?? 0);
            const uiStatus = mapEventUiStatus(event.status);
            return (
              <div
                key={event.eventId}
                className={`cursor-pointer rounded-md border p-2 transition-all hover:shadow-md ${severityClasses(level)}`}
              >
                <div className="mb-1 flex items-start justify-between gap-2">
                  <div className="flex min-w-0 flex-1 items-center gap-1.5">
                    <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
                    <span className="truncate text-xs font-semibold">{event.eventType}</span>
                  </div>
                  {uiStatus === 'active' && (
                    <Badge variant="destructive" className="text-xs">
                      진행중
                    </Badge>
                  )}
                  {uiStatus === 'reviewing' && (
                    <Badge variant="outline" className="border-orange-500 text-xs text-orange-700">
                      검토중
                    </Badge>
                  )}
                  {uiStatus === 'resolved' && (
                    <Badge variant="outline" className="border-gray-400 text-xs text-gray-600">
                      완료
                    </Badge>
                  )}
                </div>
                <div className="flex items-center justify-between text-[10px]">
                  <span className="opacity-80">{formatRelativeMinutes(event.detectedAt)}</span>
                  <span className="opacity-80">신뢰도 {Math.round(event.confidence ?? 0)}%</span>
                </div>
              </div>
            );
          })}
          {hasMore && !expanded && (
            <div className="flex items-center justify-end pt-0.5">
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="h-8 w-8 shrink-0 p-0"
                aria-label={`이상 알림 ${events.length - 3}건 더 보기`}
                onClick={onToggleExpand}
              >
                <Plus className="h-4 w-4" />
              </Button>
            </div>
          )}
          {hasMore && expanded && (
            <div className="flex items-center justify-end pt-0.5">
              <Button type="button" variant="ghost" size="sm" className="h-7 text-xs text-gray-600" onClick={onToggleExpand}>
                접기
              </Button>
            </div>
          )}
        </div>
      ) : (
        <div className="flex items-center justify-center py-0.5">
          <p className="text-xs text-gray-400">이상 없음</p>
        </div>
      )}
    </div>
  );
}
