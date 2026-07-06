'use client';

import type { RefObject } from 'react';
import { Camera, ChevronLeft, ChevronRight, ExternalLink } from 'lucide-react';
import { Button } from '@/components/shared/ui/button';
import type { CctvCameraVO } from '@/types/cctv.vo';

export type CctvFullscreenPlayerProps = {
  containerRef: RefObject<HTMLDivElement | null>;
  camera: CctvCameraVO;
  subLine: string;
  embedUrl: string | null;
  isVideoPaused: boolean;
  quickPlaylistIndex: number;
  totalCount: number;
  onClose: () => void;
  onStep: (delta: number) => void;
};

/** 전용 전체화면 순회 플레이어(원본 CctvDashboardPage :1221-1305). 호출측은 열려 있을 때만 렌더한다. */
export function CctvFullscreenPlayer({
  containerRef,
  camera,
  subLine,
  embedUrl,
  isVideoPaused,
  quickPlaylistIndex,
  totalCount,
  onClose,
  onStep,
}: CctvFullscreenPlayerProps) {
  return (
    <div
      ref={containerRef}
      id="cctv-playlist-fullscreen-pane"
      className="fixed inset-0 z-[100] flex flex-col bg-black text-white"
    >
      <div className="flex shrink-0 items-center justify-between gap-2 border-b border-white/15 px-3 py-2">
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold">{camera.cameraName}</p>
          <p className="truncate text-xs text-white/70">{subLine}</p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {embedUrl ? (
            <a
              href={embedUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-0.5 rounded bg-white/10 px-2 py-1 text-xs hover:bg-white/20"
            >
              <ExternalLink className="h-3 w-3" />
              새 창
            </a>
          ) : null}
          <Button
            type="button"
            size="sm"
            variant="secondary"
            className="bg-white/15 text-white hover:bg-white/25"
            onClick={onClose}
          >
            닫기
          </Button>
        </div>
      </div>
      <div className="relative w-full bg-black">
        {/* 16:9 비율을 유지하면서 영역을 가득 채우도록 패딩으로 높이를 확보 */}
        <div className="pointer-events-none block w-full pb-[56.25%]" aria-hidden />
        {embedUrl && !isVideoPaused ? (
          <iframe
            title={camera.cameraName}
            src={embedUrl}
            className="absolute inset-0 h-full w-full border-0"
            allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
            referrerPolicy="no-referrer-when-downgrade"
          />
        ) : (
          <div className="absolute inset-0 flex items-center justify-center">
            <Camera className="h-20 w-20 text-gray-600" />
          </div>
        )}
      </div>
      <div
        className="flex shrink-0 items-center justify-center gap-2 border-t border-white/15 bg-black/90 px-3 py-3"
        onClick={(e) => e.stopPropagation()}
      >
        <Button
          type="button"
          size="sm"
          variant="secondary"
          className="bg-white/15 text-white hover:bg-white/25"
          disabled={quickPlaylistIndex <= 0}
          onClick={() => onStep(-1)}
        >
          <ChevronLeft className="mr-0.5 h-4 w-4" />
          이전
        </Button>
        <span className="rounded bg-white/10 px-2 py-1.5 text-xs font-medium">
          {quickPlaylistIndex + 1} / {totalCount}
        </span>
        <Button
          type="button"
          size="sm"
          variant="secondary"
          className="bg-white/15 text-white hover:bg-white/25"
          disabled={quickPlaylistIndex >= totalCount - 1}
          onClick={() => onStep(1)}
        >
          다음
          <ChevronRight className="ml-0.5 h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
