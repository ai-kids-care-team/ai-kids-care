import { useEffect } from 'react';
import { X, Camera, Circle } from 'lucide-react';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import type { Camera as CameraType } from '../types/anomaly';

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
    <div className="fixed inset-0 bg-black z-50 flex flex-col">
      <div className="absolute top-4 left-4 right-4 flex items-center justify-between z-10">
        <div className="bg-black/80 backdrop-blur-sm px-4 py-2 rounded-lg">
          <h2 className="text-white font-semibold">{camera.name}</h2>
          <p className="text-white/70 text-sm">{camera.location}</p>
        </div>

        <Button
          variant="ghost"
          size="sm"
          onClick={onClose}
          className="bg-black/80 backdrop-blur-sm text-white hover:bg-black/90 rounded-full w-10 h-10 p-0"
        >
          <X className="w-6 h-6" />
        </Button>
      </div>

      <div className="flex-1 flex items-center justify-center p-20">
        <div className="relative w-full h-full bg-gray-900 rounded-lg overflow-hidden">
          <div className="absolute inset-0 flex items-center justify-center">
            <Camera className="w-32 h-32 text-gray-700" />
          </div>

          {camera.isRecording && camera.status === 'online' && (
            <div className="absolute top-6 left-6 flex items-center gap-2 bg-red-600 px-4 py-2 rounded-lg">
              <Circle className="w-4 h-4 text-white fill-white animate-pulse" />
              <span className="text-white text-lg font-semibold">REC</span>
            </div>
          )}

          <div className="absolute top-6 right-6">
            {camera.status === 'online' ? (
              <Badge className="bg-green-500 hover:bg-green-600 text-lg px-3 py-1">온라인</Badge>
            ) : camera.status === 'offline' ? (
              <Badge variant="destructive" className="text-lg px-3 py-1">오프라인</Badge>
            ) : (
              <Badge variant="secondary" className="text-lg px-3 py-1">점검중</Badge>
            )}
          </div>

          <div className="absolute bottom-6 left-6 bg-black/80 backdrop-blur-sm px-4 py-2 rounded-lg text-white text-lg font-mono">
            {new Date().toLocaleString('ko-KR', {
              year: 'numeric',
              month: '2-digit',
              day: '2-digit',
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit',
              hour12: false
            })}
          </div>

          <div className="absolute bottom-6 right-6 bg-black/80 backdrop-blur-sm px-4 py-2 rounded-lg text-white text-lg font-mono">
            {camera.id}
          </div>
        </div>
      </div>

      <div className="absolute bottom-4 left-1/2 -translate-x-1/2">
        <div className="bg-black/80 backdrop-blur-sm px-4 py-2 rounded-lg text-white text-sm">
          ESC 키를 눌러 종료
        </div>
      </div>
    </div>
  );
}
