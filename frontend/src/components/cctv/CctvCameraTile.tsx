'use client';

import { Camera, Circle, Eye, ExternalLink, Maximize2 } from 'lucide-react';
import { Button } from '@/components/shared/ui/button';
import { Card } from '@/components/shared/ui/card';
import type { CctvCameraVO } from '@/types/cctv.vo';
import type { DetectionEventListItem } from '@/services/apis/detectionEvents.api';
import { displayCameraCode, displayLocationLine, formatOverlayTime } from '@/lib/cctvFormat';
import { mapCameraLineStatus } from '@/components/cctv/hooks/useCctvGridData';
import {
  padSlotSubLine,
  padSlotTitleName,
  resolvePadSlotEmbedUrl,
  resolveRealCameraTileEmbedUrl,
} from '@/components/cctv/gridSlotResolvers';
import { CctvTileAlertList } from '@/components/cctv/CctvTileAlertList';

export type CctvCameraTileProps = {
  camera: CctvCameraVO | null;
  gridSlot: number;
  canViewLiveStreams: boolean;
  isVideoPaused: boolean;
  events: DetectionEventListItem[];
  tileAlertsExpanded: boolean;
  onToggleTileAlertsExpanded: (cameraId: number) => void;
  onOpenDetail: (camera: CctvCameraVO) => void;
  onOpenFullscreen: (camera: CctvCameraVO) => void;
};

/** 그리드의 카메라 1칸(원본 CctvDashboardPage :834-1002, 빈 슬롯 패드 포함). */
export function CctvCameraTile({
  camera,
  gridSlot,
  canViewLiveStreams,
  isVideoPaused,
  events,
  tileAlertsExpanded,
  onToggleTileAlertsExpanded,
  onOpenDetail,
  onOpenFullscreen,
}: CctvCameraTileProps) {
  const isPadSlot = camera == null;
  const padStream = isPadSlot ? resolvePadSlotEmbedUrl(gridSlot) : null;
  const streamEmbedUrl = !canViewLiveStreams
    ? null
    : isPadSlot
      ? padStream
      : resolveRealCameraTileEmbedUrl(gridSlot);

  const lineStatus = !isPadSlot ? mapCameraLineStatus(camera.status) : 'online';
  const titleName = !isPadSlot ? camera.cameraName : padSlotTitleName(gridSlot, padStream);
  const subLine = !isPadSlot ? displayLocationLine(camera) : padSlotSubLine(padStream);
  const codeLabel = !isPadSlot ? displayCameraCode(camera) : `EXT-${String(gridSlot + 1).padStart(3, '0')}`;
  const showLiveBadge = Boolean(
    streamEmbedUrl && !isVideoPaused && (isPadSlot ? Boolean(padStream) : lineStatus === 'online'),
  );

  return (
    <Card className="group relative flex w-full cursor-pointer flex-col gap-0 overflow-hidden p-0 transition-all hover:ring-2 hover:ring-purple-500">
      <div className="relative min-h-[11rem] w-full shrink-0 overflow-hidden bg-black">
        {/* aspect-ratio만 두고 자식이 전부 absolute면 높이 0으로 잡히는 브라우저 대응 */}
        <div className="pointer-events-none block w-full pb-[56.25%]" aria-hidden />
        {streamEmbedUrl && !isVideoPaused ? (
          <iframe
            title={titleName}
            src={streamEmbedUrl}
            className="absolute left-0 top-0 z-0 box-border h-full w-full min-h-[1px] border-0"
            allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
            referrerPolicy="no-referrer-when-downgrade"
          />
        ) : (
          <div className="absolute inset-0 z-0 flex items-center justify-center bg-gray-900">
            <Camera className="h-16 w-16 text-gray-700" />
          </div>
        )}
        {streamEmbedUrl && (
          <a
            href={streamEmbedUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="absolute right-2 top-10 z-30 inline-flex items-center gap-0.5 rounded bg-black/70 px-1.5 py-0.5 text-[10px] font-medium text-white backdrop-blur-sm hover:bg-black/90"
          >
            <ExternalLink className="h-3 w-3 shrink-0" />
            새 창
          </a>
        )}
        <div className="absolute left-2 top-2 z-10 rounded-md bg-black/80 px-3 py-2 shadow-lg backdrop-blur-md">
          <div className="text-sm font-semibold leading-tight text-white">{titleName}</div>
          <div className="mt-0.5 text-xs text-gray-300">{subLine}</div>
        </div>
        <div className="absolute right-2 top-2 z-10 rounded bg-black/70 px-2 py-1 font-mono text-xs text-white backdrop-blur-sm">
          {codeLabel}
        </div>
        <div className="absolute bottom-2 left-2 z-10 rounded bg-black/70 px-2 py-1 font-mono text-xs text-white backdrop-blur-sm">
          {formatOverlayTime()}
        </div>
        {showLiveBadge && (
          <div className="absolute bottom-2 right-2 z-10 flex items-center gap-1.5 rounded bg-emerald-600/90 px-2.5 py-1 shadow-lg backdrop-blur-sm">
            <Circle className="h-2 w-2 animate-pulse fill-white text-white" />
            <span className="text-xs font-semibold text-white">LIVE</span>
          </div>
        )}

        <div className="absolute inset-0 z-20 flex items-center justify-center gap-2 bg-black/50 opacity-0 transition-opacity group-hover:opacity-100">
          <Button
            size="sm"
            className="bg-white/90 text-gray-900 hover:bg-white"
            type="button"
            onClick={() => camera && onOpenDetail(camera)}
          >
            <Eye className="mr-1 h-4 w-4" />
            상세보기
          </Button>
          <Button
            size="sm"
            variant="outline"
            className="bg-white/90 text-gray-900 hover:bg-white"
            type="button"
            onClick={() => camera && onOpenFullscreen(camera)}
          >
            <Maximize2 className="mr-1 h-4 w-4" />
            전체화면
          </Button>
        </div>
      </div>

      <CctvTileAlertList
        events={events}
        expanded={!isPadSlot && tileAlertsExpanded}
        onToggleExpand={() => camera && onToggleTileAlertsExpanded(camera.cameraId)}
      />
    </Card>
  );
}
