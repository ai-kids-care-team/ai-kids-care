'use client';

import { useEffect, useRef } from 'react';
import Hls from 'hls.js'; // 👈 hls.js 라이브러리 추가
import { Camera, Circle, Eye, Maximize2 } from 'lucide-react';
import { Card } from './ui/card';
import { Badge } from './ui/badge';
import { Button } from './ui/button';
import type { Camera as CameraType } from '../types/anomaly';

// 💡 HLS 비디오 스트리밍을 처리하는 내부 컴포넌트
function HlsVideoPlayer({ streamUrl }: { streamUrl: string }) {
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !streamUrl) return;

    let hls: Hls;

    // HLS.js가 지원되는 브라우저 (대부분의 모던 브라우저)
    if (Hls.isSupported()) {
      hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true, // 실시간 CCTV에 맞게 지연시간 최소화
      });
      hls.loadSource(streamUrl);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        video.play().catch((e) => console.log('자동 재생 방지됨', e));
      });
    }
    // Safari처럼 HLS를 네이티브로 지원하는 브라우저
    else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = streamUrl;
      video.addEventListener('loadedmetadata', () => {
        video.play().catch((e) => console.log('자동 재생 방지됨', e));
      });
    }

    return () => {
      if (hls) {
        hls.destroy(); // 메모리 누수 방지
      }
    };
  }, [streamUrl]);

  return (
    <video
      ref={videoRef}
      className="absolute inset-0 w-full h-full object-cover pointer-events-none" // 클릭 방해 금지
      autoPlay
      muted
      playsInline
    />
  );
}

interface CCTVGridProps {
  cameras: CameraType[];
  onCameraSelect?: (cameraId: string) => void;
  onCameraFullscreen?: (cameraId: string) => void;
  layout?: '2x2' | '3x3' | '1x1';
}

export function CCTVGrid({ cameras, onCameraSelect, onCameraFullscreen, layout = '2x2' }: CCTVGridProps) {
  const gridCols = layout === '1x1' ? 'grid-cols-1' : layout === '2x2' ? 'grid-cols-2' : 'grid-cols-3';
  const displayCameras = layout === '1x1' ? cameras.slice(0, 1) : layout === '2x2' ? cameras.slice(0, 4) : cameras.slice(0, 9);

  return (
    <div className={`grid ${gridCols} gap-3 h-full`}>
      {displayCameras.map((camera) => (
        <Card
          key={camera.id}
          className="overflow-hidden cursor-pointer hover:ring-2 hover:ring-purple-500 transition-all group relative"
          onClick={() => onCameraSelect?.(camera.id)}
        >
          <div className="relative aspect-video bg-gray-900 overflow-hidden">

            {/* 💡 streamUrl이 있으면 실제 영상 재생, 없으면 기존 아이콘 표시 */}
            {camera.streamUrl ? (
              <HlsVideoPlayer streamUrl={camera.streamUrl} />
            ) : (
              <div className="absolute inset-0 flex items-center justify-center">
                <Camera className="w-16 h-16 text-gray-700" />
              </div>
            )}

            {/* Recording Indicator */}
            {camera.isRecording && camera.status === 'online' && (
              <div className="absolute top-2 left-2 flex items-center gap-1.5 bg-red-600 px-2 py-1 rounded">
                <Circle className="w-2 h-2 text-white fill-white animate-pulse" />
                <span className="text-white text-xs font-semibold">REC</span>
              </div>
            )}

            {/* Camera ID */}
            <div className="absolute top-2 right-2 bg-black/70 backdrop-blur-sm px-2 py-1 rounded text-white text-xs font-mono">
              {camera.id}
            </div>

            {/* Status Badge */}
            <div className="absolute top-2 left-1/2 -translate-x-1/2">
              {camera.status === 'online' ? (
                <Badge className="bg-green-500/90 hover:bg-green-600 backdrop-blur-sm">온라인</Badge>
              ) : camera.status === 'offline' ? (
                <Badge variant="destructive" className="backdrop-blur-sm">오프라인</Badge>
              ) : (
                <Badge variant="secondary" className="backdrop-blur-sm">점검중</Badge>
              )}
            </div>

            {/* Timestamp overlay */}
            <div className="absolute bottom-2 left-2 bg-black/70 backdrop-blur-sm px-2 py-1 rounded text-white text-xs font-mono">
              {new Date().toLocaleString('ko-KR', {
                year: 'numeric', month: '2-digit', day: '2-digit',
                hour: '2-digit', minute: '2-digit', second: '2-digit',
                hour12: false
              })}
            </div>

            {/* Hover Actions */}
            <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-2">
              <Button
                size="sm"
                className="bg-white/90 hover:bg-white text-gray-900"
                onClick={(e: React.MouseEvent<HTMLButtonElement>) => {
                  e.stopPropagation();
                  onCameraSelect?.(camera.id);
                }}
              >
                <Eye className="w-4 h-4 mr-1" />
                상세보기
              </Button>
              <Button
                size="sm"
                variant="outline"
                className="bg-white/90 hover:bg-white text-gray-900 z-10"
                onClick={(e: React.MouseEvent<HTMLButtonElement>) => {
                  e.stopPropagation();
                  onCameraFullscreen?.(camera.id); // 전체화면 핸들러로 수정
                }}
              >
                <Maximize2 className="w-4 h-4 mr-1" />
                전체화면
              </Button>
            </div>
          </div>

          {/* Camera Info */}
          <div className="p-3 bg-white">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-semibold text-sm text-gray-900">{camera.name}</h3>
                <p className="text-xs text-gray-500 mt-0.5">{camera.location}</p>
              </div>
            </div>
          </div>
        </Card>
      ))}
    </div>
  );
}