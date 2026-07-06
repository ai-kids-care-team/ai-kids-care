'use client';

import { AlertTriangle } from 'lucide-react';
import { Badge } from '@/components/shared/ui/badge';
import { Button } from '@/components/shared/ui/button';
import { Card } from '@/components/shared/ui/card';
import type { CctvCameraVO } from '@/types/cctv.vo';
import type { DetectionEventListItem } from '@/services/apis/detectionEvents.api';
import { severityLevel } from '@/lib/severity';
import { displayLocationLine, formatRelativeMinutes } from '@/lib/cctvFormat';
import { mapEventUiStatus } from '@/components/cctv/hooks/useCctvGridData';

export type CctvAlertPanelProps = {
  events: DetectionEventListItem[];
  cameras: CctvCameraVO[];
  highlightedEventIds: Set<number>;
  onClose: () => void;
};

/** 하단 전체 이상상황 알림 패널 — 실시간 알림의 주요 전시 위치(원본 :1139-1219). */
export function CctvAlertPanel({ events, cameras, highlightedEventIds, onClose }: CctvAlertPanelProps) {
  return (
    <div className="max-h-96 shrink-0 overflow-hidden border-t border-gray-200 bg-gray-50 p-4">
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <AlertTriangle className="h-5 w-5 text-red-600" />
          <h3 className="font-semibold text-gray-900">전체 이상상황 알림</h3>
          <Badge className="bg-red-500 hover:bg-red-600">{events.length}건</Badge>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="text-gray-500 hover:text-gray-700"
          onClick={onClose}
        >
          닫기
        </Button>
      </div>
      <div className="grid max-h-72 grid-cols-1 gap-3 overflow-y-auto md:grid-cols-2 lg:grid-cols-3">
        {events.length === 0 ? (
          <div className="col-span-full py-8 text-center text-gray-500">이상상황 알림이 없습니다</div>
        ) : (
          events.slice(0, 12).map((event) => {
            const cam = cameras.find((c) => c.cameraId === event.cameraId);
            const level = severityLevel(event.severity ?? 0);
            const uiStatus = mapEventUiStatus(event.status);
            const isHighlighted = highlightedEventIds.has(event.eventId);
            const borderBg =
              level === 'high'
                ? 'border-red-200 bg-red-50'
                : level === 'medium'
                  ? 'border-orange-200 bg-orange-50'
                  : 'border-yellow-200 bg-yellow-50';
            return (
              <Card
                key={event.eventId}
                className={`cursor-pointer p-4 hover:shadow-lg ${borderBg} ${
                  isHighlighted ? 'ring-2 ring-emerald-400' : ''
                }`}
              >
                <div className="mb-3 flex items-start justify-between">
                  <div className="flex items-center gap-2">
                    <AlertTriangle className="h-4 w-4 shrink-0 text-red-600" />
                    <h4 className="text-sm font-semibold text-gray-900">{event.eventType ?? '알 수 없음'}</h4>
                  </div>
                  <Badge variant={level === 'high' ? 'destructive' : 'secondary'} className="text-xs">
                    {level === 'high' ? '높음' : level === 'medium' ? '중간' : '낮음'}
                  </Badge>
                </div>
                <div className="space-y-2 text-sm">
                  <p className="text-gray-700">
                    {cam?.cameraName ?? event.cameraName ?? `cameraId ${event.cameraId ?? '-'}`}{' '}
                    <span className="text-gray-500">({cam ? displayLocationLine(cam) : event.roomName ?? '—'})</span>
                  </p>
                  <p className="text-xs text-gray-600">
                    {formatRelativeMinutes(event.detectedAt)} · 신뢰도 {Math.round(event.confidence ?? 0)}%
                  </p>
                  <div className="flex justify-between border-t border-gray-200 pt-2 text-xs">
                    {uiStatus === 'active' && <Badge variant="destructive">진행중</Badge>}
                    {uiStatus === 'reviewing' && (
                      <Badge variant="outline" className="border-orange-500 text-orange-700">
                        검토중
                      </Badge>
                    )}
                    {uiStatus === 'resolved' && (
                      <Badge variant="outline" className="border-gray-400 text-gray-600">
                        완료
                      </Badge>
                    )}
                  </div>
                </div>
              </Card>
            );
          })
        )}
      </div>
    </div>
  );
}
