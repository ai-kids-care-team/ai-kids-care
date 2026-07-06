'use client';

import { Grid2x2, Grid3x3, Pause, Play, Square, Video } from 'lucide-react';
import { Button } from '@/components/shared/ui/button';
import { Card } from '@/components/shared/ui/card';
import { ScrollArea } from '@/components/shared/ui/scroll-area';
import type { LayoutMode } from '@/components/cctv/hooks/useCctvGridData';

export type CctvControlPanelProps = {
  layout: LayoutMode;
  onChangeLayout: (layout: LayoutMode) => void;
  hasFilteredCameras: boolean;
  onOpenFullscreenPlaylist: () => void;
  isVideoPaused: boolean;
  onTogglePause: () => void;
};

/** 우측 제어 패널 — 피그마 RightPanel.tsx (원본 CctvDashboardPage :1041-1136). */
export function CctvControlPanel({
  layout,
  onChangeLayout,
  hasFilteredCameras,
  onOpenFullscreenPlaylist,
  isVideoPaused,
  onTogglePause,
}: CctvControlPanelProps) {
  return (
    <div className="flex h-full w-80 shrink-0 flex-col border-l border-gray-200 bg-white">
      <div className="border-b border-gray-200 p-4">
        <h2 className="text-sm font-semibold text-gray-900">제어 패널</h2>
        <p className="mt-1 text-xs text-gray-500">카메라 제어 및 설정</p>
      </div>
      <ScrollArea className="min-h-0 flex-1 p-4">
        <div className="space-y-4">
          <Card className="p-4">
            <h3 className="mb-3 text-sm font-semibold text-gray-900">화면 레이아웃</h3>
            <div className="grid grid-cols-3 gap-2">
              <Button
                type="button"
                variant={layout === '1x1' ? 'default' : 'outline'}
                size="sm"
                className="flex h-auto flex-col gap-1 py-2"
                onClick={() => onChangeLayout('1x1')}
              >
                <Square className="h-5 w-5" />
                <span className="text-xs">1×1</span>
              </Button>
              <Button
                type="button"
                variant={layout === '2x2' ? 'default' : 'outline'}
                size="sm"
                className="flex h-auto flex-col gap-1 py-2"
                onClick={() => onChangeLayout('2x2')}
              >
                <Grid2x2 className="h-5 w-5" />
                <span className="text-xs">2×2</span>
              </Button>
              <Button
                type="button"
                variant={layout === '3x3' ? 'default' : 'outline'}
                size="sm"
                className="flex h-auto flex-col gap-1 py-2"
                onClick={() => onChangeLayout('3x3')}
              >
                <Grid3x3 className="h-5 w-5" />
                <span className="text-xs">3×3</span>
              </Button>
            </div>
          </Card>

          <Card className="p-4">
            <h3 className="mb-3 text-sm font-semibold text-gray-900">빠른 작업</h3>
            <div className="space-y-2">
              <Button
                variant="outline"
                size="sm"
                className="w-full justify-start gap-2"
                type="button"
                disabled={!hasFilteredCameras}
                onClick={onOpenFullscreenPlaylist}
              >
                <Video className="h-4 w-4" />
                전체 화면 보기
              </Button>
              <Button
                variant="outline"
                size="sm"
                className="w-full justify-start gap-2"
                type="button"
                onClick={onTogglePause}
              >
                {isVideoPaused ? (
                  <>
                    <Play className="h-4 w-4" />
                    재생
                  </>
                ) : (
                  <>
                    <Pause className="h-4 w-4" />
                    일시정지
                  </>
                )}
              </Button>
              {/* 빠른 작업의 영상 다운로드 버튼은 현재 사용하지 않으므로 숨김 */}
            </div>
          </Card>
        </div>
      </ScrollArea>
    </div>
  );
}
