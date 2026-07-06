'use client';

import { Button } from '@/components/shared/ui/button';
import type { CctvCameraVO } from '@/types/cctv.vo';
import type { CameraStreamVO } from '@/services/apis/cctv.api';
import { displayCameraCode } from '@/lib/cctvFormat';

export type CctvCameraDetailModalProps = {
  camera: CctvCameraVO;
  kindergartenName: string | null;
  canViewLiveStreams: boolean;
  streams: CameraStreamVO[];
  onClose: () => void;
};

/** 카메라 상세정보 모달(원본 CctvDashboardPage :1306-1366). */
export function CctvCameraDetailModal({
  camera,
  kindergartenName,
  canViewLiveStreams,
  streams,
  onClose,
}: CctvCameraDetailModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="w-full max-w-2xl rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b px-5 py-3">
          <h3 className="text-base font-semibold text-gray-900">카메라 상세정보</h3>
          <Button type="button" size="sm" variant="ghost" onClick={onClose}>
            닫기
          </Button>
        </div>
        <div className="space-y-4 p-5">
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <p className="text-gray-500">카메라명</p>
              <p className="font-medium text-gray-900">{camera.cameraName}</p>
            </div>
            <div>
              <p className="text-gray-500">카메라 코드</p>
              <p className="font-medium text-gray-900">{displayCameraCode(camera)}</p>
            </div>
            <div>
              <p className="text-gray-500">유치원</p>
              <p className="font-medium text-gray-900">{kindergartenName ?? '불러오는 중…'}</p>
            </div>
            <div>
              <p className="text-gray-500">상태</p>
              <p className="font-medium text-gray-900">{camera.status}</p>
            </div>
          </div>
          <div>
            <p className="mb-2 text-sm font-semibold text-gray-800">스트림 설정 (백엔드 `camera_streams`)</p>
            {!canViewLiveStreams ? (
              <p className="text-sm text-amber-700">
                이 역할에는 live stream 접근 권한이 제공되지 않습니다.
              </p>
            ) : streams.length === 0 ? (
              <p className="text-sm text-gray-500">연결된 스트림 설정이 없습니다.</p>
            ) : (
              <div className="space-y-2">
                {streams.map((s, idx) => (
                  <div
                    key={s.streamId != null ? `stream-${s.streamId}` : `stream-${camera.cameraId}-${idx}`}
                    className="rounded border bg-gray-50 p-2 text-xs"
                  >
                    <p>
                      #{s.streamId ?? idx + 1} · {s.playbackProtocol ?? s.sourceProtocol ?? 'UNKNOWN'} ·{' '}
                      {s.streamType ?? 'N/A'} · {s.enabled ? 'ENABLED' : 'DISABLED'}
                    </p>
                    <p className="text-gray-600">재생 주소는 공개 API에서 제공하지 않습니다.</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
