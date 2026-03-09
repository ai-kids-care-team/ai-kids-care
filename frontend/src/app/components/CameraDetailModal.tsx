import { X, Camera, Circle, Maximize2, Download, AlertTriangle, MapPin } from 'lucide-react';
import { Card } from './ui/card';
import { Badge } from './ui/badge';
import { Button } from './ui/button';
import type { Camera as CameraType } from '../types/anomaly';

interface CameraDetailModalProps {
  camera: CameraType;
  onClose: () => void;
  onFullscreen: () => void;
}

export function CameraDetailModal({ camera, onClose, onFullscreen }: CameraDetailModalProps) {
  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <Card className="w-full max-w-4xl max-h-[90vh] overflow-y-auto bg-white">
        <div className="sticky top-0 bg-white border-b border-gray-200 p-4 flex items-center justify-between z-10">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">{camera.name}</h2>
            <p className="text-sm text-gray-500">{camera.location}</p>
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={onFullscreen}
              className="gap-2"
            >
              <Maximize2 className="w-4 h-4" />
              전체화면
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={onClose}
              className="rounded-full w-8 h-8 p-0"
            >
              <X className="w-5 h-5" />
            </Button>
          </div>
        </div>

        <div className="p-6 space-y-6">
          <div className="relative aspect-video bg-gray-900 rounded-lg overflow-hidden">
            <div className="absolute inset-0 flex items-center justify-center">
              <Camera className="w-24 h-24 text-gray-700" />
            </div>

            {camera.isRecording && camera.status === 'online' && (
              <div className="absolute top-4 left-4 flex items-center gap-2 bg-red-600 px-3 py-2 rounded-lg">
                <Circle className="w-3 h-3 text-white fill-white animate-pulse" />
                <span className="text-white text-sm font-semibold">REC</span>
              </div>
            )}

            <div className="absolute top-4 right-4">
              {camera.status === 'online' ? (
                <Badge className="bg-green-500 hover:bg-green-600">온라인</Badge>
              ) : camera.status === 'offline' ? (
                <Badge variant="destructive">오프라인</Badge>
              ) : (
                <Badge variant="secondary">점검중</Badge>
              )}
            </div>

            <div className="absolute bottom-4 left-4 bg-black/70 backdrop-blur-sm px-3 py-2 rounded-lg text-white text-sm font-mono">
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
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Card className="p-4 bg-gray-50">
              <h3 className="text-sm font-semibold text-gray-900 mb-3">카메라 정보</h3>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-600">카메라 ID</span>
                  <span className="font-mono font-medium">{camera.id}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">위치</span>
                  <span className="font-medium">{camera.location}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">카테고리</span>
                  <span className="font-medium">
                    {camera.category === 'entrance' && '출입구'}
                    {camera.category === 'classroom' && '교실'}
                    {camera.category === 'playground' && '놀이터'}
                    {camera.category === 'corridor' && '복도'}
                    {camera.category === 'office' && '사무실'}
                    {camera.category === 'parking' && '주차장'}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">상태</span>
                  <span className={`font-medium ${
                    camera.status === 'online' ? 'text-green-600' :
                    camera.status === 'offline' ? 'text-red-600' :
                    'text-yellow-600'
                  }`}>
                    {camera.status === 'online' && '온라인'}
                    {camera.status === 'offline' && '오프라인'}
                    {camera.status === 'maintenance' && '점검중'}
                  </span>
                </div>
              </div>
            </Card>

            <Card className="p-4 bg-gray-50">
              <h3 className="text-sm font-semibold text-gray-900 mb-3">녹화 정보</h3>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-600">녹화 상태</span>
                  <span className={`font-medium ${camera.isRecording ? 'text-red-600' : 'text-gray-600'}`}>
                    {camera.isRecording ? '녹화중' : '중지'}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">해상도</span>
                  <span className="font-medium">1920×1080</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">프레임</span>
                  <span className="font-medium">30 FPS</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">비트레이트</span>
                  <span className="font-medium">4 Mbps</span>
                </div>
              </div>
            </Card>
          </div>

          <Card className="p-4 bg-blue-50 border-blue-200">
            <div className="flex items-start gap-3">
              <AlertTriangle className="w-5 h-5 text-blue-600 mt-0.5" />
              <div>
                <h3 className="text-sm font-semibold text-blue-900 mb-1">AI 이상행동 감지 활성화</h3>
                <p className="text-xs text-blue-700">
                  12가지 이상행동 패턴을 실시간으로 분석하고 있습니다.
                </p>
              </div>
            </div>
          </Card>

          <div className="flex gap-2">
            <Button className="flex-1 gap-2">
              <Download className="w-4 h-4" />
              영상 다운로드
            </Button>
            <Button variant="outline" className="flex-1 gap-2">
              <MapPin className="w-4 h-4" />
              위치 보기
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
}
