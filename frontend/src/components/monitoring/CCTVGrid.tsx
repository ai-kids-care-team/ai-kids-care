'use client';

import { useEffect, useRef } from 'react';
import Hls from 'hls.js'; // 👈 hls.js 라이브러리 추가
import { Camera, Circle, Eye, Maximize2 } from 'lucide-react';
import { Card } from '@/components/shared/ui/card';
import { Badge } from '@/components/shared/ui/badge';
import { Button } from '@/components/shared/ui/button';
import type { Camera as CameraType, AnomalyEvent } from '../../types/anomaly';
import { anomalyTypeLabels, anomalyTypeColors } from '../../types/anomaly';

// 💡 HLS 비디오 스트리밍을 처리하는 내부 컴포넌트 (isPaused: 해당 카메라만 일시정지)
function HlsVideoPlayer({ streamUrl, isPaused = false }: { streamUrl: string; isPaused?: boolean }) {
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
        if (!isPaused) video.play().catch((e) => console.log('자동 재생 방지됨', e));
      });
    }
    // Safari처럼 HLS를 네이티브로 지원하는 브라우저
    else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = streamUrl;
      video.addEventListener('loadedmetadata', () => {
        if (!isPaused) video.play().catch((e) => console.log('자동 재생 방지됨', e));
      });
    }

    return () => {
      if (hls) {
        hls.destroy(); // 메모리 누수 방지
      }
    };
  }, [streamUrl]);

  // 카메라별 일시정지 제어 (빠른 작업에서 선택한 카메라만 적용)
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    if (isPaused) video.pause();
    else video.play().catch(() => {});
  }, [isPaused]);

  return (
    <video
      ref={videoRef}
      className="absolute inset-0 w-full h-full object-cover pointer-events-none"
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
  events?: AnomalyEvent[];
  onEventClick?: (eventId: string) => void;
  /** 일시정지된 카메라 ID 집합 (빠른 작업에서 카메라별 일시정지 반영) */
  pausedCameraIds?: Set<string>;
}

export function CCTVGrid({ cameras, onCameraSelect, onCameraFullscreen, layout = '2x2', events, onEventClick, pausedCameraIds }: CCTVGridProps) {
  const gridCols = layout === '1x1' ? 'grid-cols-1' : layout === '2x2' ? 'grid-cols-2' : 'grid-cols-3';
  const displayCameras = layout === '1x1' ? cameras.slice(0, 1) : layout === '2x2' ? cameras.slice(0, 4) : cameras.slice(0, 9);

  const getLatestEventForCamera = (cameraId: string) => {
    if (!events || events.length === 0) return null;
    const related = events.filter((e) => e.cameraId === cameraId);
    if (related.length === 0) return null;
    // 최신 이벤트가 배열 앞에 있다고 가정
    return related[0];
  };

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
              <HlsVideoPlayer
                streamUrl={camera.streamUrl}
                isPaused={pausedCameraIds?.has(camera.id)}
              />
            ) : (
              <div className="absolute inset-0 flex items-center justify-center">
                <Camera className="w-16 h-16 text-gray-700" />
              </div>
            )}

            {/* 카메라 이름 + 위치 (기존 REC 위치로 이동) */}
            <div className="absolute top-2 left-2 bg-indigo-600/95 backdrop-blur-sm px-2.5 py-1.5 rounded-md text-white text-[10px] max-w-[70%] shadow-lg shadow-black/40 border border-white/15">
              <div className="font-semibold tracking-tight truncate">
                {camera.name}
              </div>
              <div className="text-[9px] text-white/90 truncate">
                {camera.location}
              </div>
            </div>

            {/* Camera ID */}
            <div className="absolute top-2 right-2 bg-black/70 backdrop-blur-sm px-2 py-1 rounded text-white text-xs font-mono">
              {camera.id}
            </div>

            {/* 녹화 중일 때만 표시 (전체 녹화 제어 반영) */}
            {camera.isRecording && camera.status === 'online' && (
              <div className="absolute bottom-2 right-2 flex items-center gap-1 bg-red-600 px-2 py-0.5 rounded text-white text-[10px] font-semibold">
                <Circle className="w-1.5 h-1.5 fill-white animate-pulse" />
                REC
              </div>
            )}

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

          {/* 하단 정보 영역: 이상행동 알림 + 오프라인 알람 */}
          <div className="p-3 bg-white space-y-2">
            {/* 오프라인 알람 */}
            {camera.status === 'offline' && (
              <div className="w-full rounded border border-gray-300 bg-gray-100 text-[11px] text-gray-700 px-2 py-1 flex items-center justify-between">
                <span className="font-medium">오프라인</span>
                <span className="text-[10px] text-gray-500">영상 신호 없음</span>
              </div>
            )}

            {/* 이상행동 알림 (확률별 색상 / 애니메이션) */}
            {(() => {
              const latestEvent = getLatestEventForCamera(camera.id);

              // 이벤트가 없으면 "이상 없음" (초록색)
              if (!latestEvent) {
                return (
                  <div className="flex items-center justify-between w-full rounded px-2 py-1 text-[11px] bg-emerald-50 text-emerald-700 border border-emerald-200">
                    <span className="font-medium">이상 없음</span>
                    <span className="text-[10px] text-emerald-500">정상</span>
                  </div>
                );
              }

              const confidence = latestEvent.confidence ?? 0;
              // 상태 한글 라벨
              const statusLabel =
                latestEvent.status === 'resolved'
                  ? '처리 완료'
                  : latestEvent.status === 'reviewing'
                  ? '검토 중'
                  : '검토 전';
              let colorClass = 'bg-emerald-500'; // 기본값 (안 쓰이지만 안전용)
              let wrapperClass =
                'flex items-center justify-between w-full rounded px-2 py-1 text-[11px]';

              if (confidence >= 80) {
                // 80% 이상: 빨간색 + 천천히 깜빡임
                colorClass = 'bg-red-600';
                wrapperClass += ' bg-red-50 border border-red-200 text-red-700 animate-pulse';
              } else if (confidence >= 60) {
                // 60% 이상 ~ 80% 미만: 노란색
                colorClass = 'bg-yellow-500';
                wrapperClass += ' bg-yellow-50 border border-yellow-200 text-yellow-700';
              } else {
                // 60% 미만: 초록색 (완전 위험은 아님)
                colorClass = 'bg-emerald-500';
                wrapperClass += ' bg-emerald-50 border border-emerald-200 text-emerald-700';
              }

              return (
                <button
                  type="button"
                  className={wrapperClass}
                  onClick={(e) => {
                    e.stopPropagation();
                    if (onEventClick) {
                      onEventClick(latestEvent.id);
                    }
                  }}
                >
                  {/* 알림 배지 (이전 스타일로 복원) */}
                  <div
                    className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-xs text-white ${colorClass}`}
                  >
                    <span>{anomalyTypeLabels[latestEvent.type]}</span>
                    <span className="opacity-80">/ {confidence}%</span>
                  </div>

                  {/* 상태 텍스트를 알림 옆에 표시 */}
                  <span className="text-[10px] font-medium text-gray-600">
                    {statusLabel}
                  </span>

                  {/* 발생 시간 */}
                  <span className="text-[10px] text-gray-500">
                    {new Date(latestEvent.timestamp).toLocaleTimeString('ko-KR', {
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>
                </button>
              );
            })()}
          </div>
        </Card>
      ))}
    </div>
  );
}