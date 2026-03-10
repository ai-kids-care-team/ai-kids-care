'use client';

import { useEffect, useRef } from 'react';
import Hls from 'hls.js'; // 👈 hls.js 라이브러리 추가
import { X, Camera, Circle } from 'lucide-react';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import type { Camera as CameraType } from '../types/anomaly';

// 💡 전체화면용 HLS 비디오 플레이어 컴포넌트
function FullscreenHlsPlayer({ streamUrl }: { streamUrl: string }) {
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !streamUrl) return;

    let hls: Hls;

    if (Hls.isSupported()) {
      hls = new Hls({ enableWorker: true, lowLatencyMode: true });
      hls.loadSource(streamUrl);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        video.play().catch((e) => console.log('자동 재생 방지됨', e));
      });
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = streamUrl;
      video.addEventListener('loadedmetadata', () => {
        video.play().catch((e) => console.log('자동 재생 방지됨', e));
      });
    }

    return () => {
      if (hls) hls.destroy();
    };
  }, [streamUrl]);

  return (
    <video
      ref={videoRef}
      className="absolute inset-0 w-full h-full object-contain bg-black"
      autoPlay
      muted // 크롬 등에서 자동재생을 위해 muted 기본 적용
      controls // 전체화면이므로 컨트롤러 제공
      playsInline
    />
  );
}

interface FullscreenViewProps {
  camera: CameraType;
  onClose: () => void;
}

export function FullscreenView({ camera, onClose }: FullscreenViewProps) {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 bg-black z-[100] flex flex-col">
      <div className="absolute top-4 left-4 right-4 flex items-center justify-between z-10 pointer-events-none">
        <div className="bg-black/80 backdrop-blur-sm px-4 py-2 rounded-lg pointer-events-auto">
          <h2 className="text-white font-semibold">{camera.name}</h2>
          <p className="text-white/70 text-sm">{camera.location}</p>
        </div>

        <Button
          variant="ghost"
          size="sm"
          onClick={onClose}
          className="bg-black/80 backdrop-blur-sm text-white hover:bg-black/90 rounded-full w-10 h-10 p-0 pointer-events-auto"
        >
          <X className="w-6 h-6" />
        </Button>
      </div>

      <div className="flex-1 flex items-center justify-center p-0 md:p-12">
        <div className="relative w-full h-full bg-gray-900 rounded-lg overflow-hidden">

          {/* 💡 스트리밍 URL 처리 */}
          {camera.streamUrl ? (
            <FullscreenHlsPlayer streamUrl={camera.streamUrl} />
          ) : (
            <div className="absolute inset-0 flex items-center justify-center">
              <Camera className="w-32 h-32 text-gray-700" />
            </div>
          )}

          {/* 오버레이 UI들 (비디오 컨트롤러를 가리지 않도록 pointer-events-none 적용) */}
          <div className="absolute top-6 left-6 flex items-center gap-2 bg-red-600 px-4 py-2 rounded-lg pointer-events-none">
            <Circle className="w-4 h-4 text-white fill-white animate-pulse" />
            <span className="text-white text-lg font-semibold">REC</span>
          </div>

          <div className="absolute top-6 right-6 pointer-events-none">
            {camera.status === 'online' ? (
              <Badge className="bg-green-500 hover:bg-green-600 text-lg px-3 py-1">온라인</Badge>
            ) : camera.status === 'offline' ? (
              <Badge variant="destructive" className="text-lg px-3 py-1">오프라인</Badge>
            ) : (
              <Badge variant="secondary" className="text-lg px-3 py-1">점검중</Badge>
            )}
          </div>

          <div className="absolute bottom-6 left-6 bg-black/80 backdrop-blur-sm px-4 py-2 rounded-lg text-white text-lg font-mono pointer-events-none hidden md:block">
            {new Date().toLocaleString('ko-KR', {
              year: 'numeric', month: '2-digit', day: '2-digit',
              hour: '2-digit', minute: '2-digit', second: '2-digit',
              hour12: false
            })}
          </div>

          <div className="absolute bottom-6 right-6 bg-black/80 backdrop-blur-sm px-4 py-2 rounded-lg text-white text-lg font-mono pointer-events-none hidden md:block">
            {camera.id}
          </div>
        </div>
      </div>

      <div className="absolute bottom-4 left-1/2 -translate-x-1/2 pointer-events-none">
        <div className="bg-black/80 backdrop-blur-sm px-4 py-2 rounded-lg text-white text-sm">
          ESC 키를 눌러 종료
        </div>
      </div>
    </div>
  );
}