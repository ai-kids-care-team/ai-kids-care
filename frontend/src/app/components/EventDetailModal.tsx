'use client';

import { useState } from 'react';
import {
  X, Camera, AlertTriangle, MapPin, Clock, User,
  CheckCircle, Eye, XCircle, MessageSquare, Download, Share2
} from 'lucide-react';
import { Card } from './ui/card';
import { Badge } from './ui/badge';
import { Button } from './ui/button';
import type { AnomalyEvent } from '../types/anomaly';
import { anomalyTypeLabels, anomalyTypeColors } from '../types/anomaly';

interface EventDetailModalProps {
  event: AnomalyEvent;
  onClose: () => void;
  onStatusChange?: (status: AnomalyEvent['status']) => void;
  onAddNote?: (note: string) => void;
  canResolve: boolean;
}

export function EventDetailModal({ event, onClose, onStatusChange, onAddNote, canResolve }: EventDetailModalProps) {
  const [note, setNote] = useState('');
  const [showNoteInput, setShowNoteInput] = useState(false);

  const getStatusBadge = () => {
    switch (event.status) {
      case 'active':
        return <Badge className="bg-red-100 text-red-700 border-red-200">긴급 대응 필요</Badge>;
      case 'reviewing':
        return <Badge className="bg-orange-100 text-orange-700 border-orange-200">검토중</Badge>;
      case 'resolved':
        return <Badge className="bg-green-100 text-green-700 border-green-200">처리 완료</Badge>;
      default:
        return null; // TypeScript 에러 방지용
    }
  };

  const getSeverityBadge = () => {
    switch (event.severity) {
      case 'high':
        return <Badge variant="destructive">높음</Badge>;
      case 'medium':
        return <Badge className="bg-orange-500 hover:bg-orange-600">보통</Badge>;
      case 'low':
        return <Badge variant="secondary">낮음</Badge>;
      default:
        return null; // TypeScript 에러 방지용
    }
  };

  const handleAddNote = () => {
    if (note.trim() && onAddNote) {
      onAddNote(note.trim());
      setNote('');
      setShowNoteInput(false);
    }
  };

  const handleDownloadClip = () => {
    alert('해당 이벤트의 영상 클립을 다운로드합니다.\n(실제 환경에서는 전후 30초 영상이 다운로드됩니다)');
  };

  const handleShareEvent = () => {
    alert('이벤트 정보를 공유합니다.\n(실제 환경에서는 관계자에게 알림이 전송됩니다)');
  };

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <Card className="w-full max-w-3xl max-h-[90vh] overflow-y-auto bg-white">
        <div className="sticky top-0 bg-white border-b border-gray-200 p-4 flex items-center justify-between z-10">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">이상상황 상세 정보</h2>
            <p className="text-sm text-gray-500">감지 ID: {event.id}</p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={onClose}
            className="rounded-full w-8 h-8 p-0"
          >
            <X className="w-5 h-5" />
          </Button>
        </div>

        <div className="p-6 space-y-6">
          <div className="flex items-center gap-3">
            {getStatusBadge()}
            {getSeverityBadge()}
          </div>

          <div className="relative aspect-video bg-gray-900 rounded-lg overflow-hidden">
            <div className="absolute inset-0 flex items-center justify-center">
              <Camera className="w-24 h-24 text-gray-700" />
            </div>

            <div className={`absolute top-4 left-4 inline-flex items-center gap-2 px-3 py-2 rounded-lg ${anomalyTypeColors[event.type]} text-white`}>
              <AlertTriangle className="w-5 h-5" />
              <span className="font-semibold">{anomalyTypeLabels[event.type]}</span>
            </div>

            <div className="absolute bottom-4 left-4 bg-black/70 backdrop-blur-sm px-3 py-2 rounded-lg text-white text-sm font-mono">
              {event.timestamp.toLocaleString('ko-KR', {
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
              <h3 className="text-sm font-semibold text-gray-900 mb-3">감지 정보</h3>
              <div className="space-y-3 text-sm">
                <div className="flex items-start gap-2">
                  <AlertTriangle className="w-4 h-4 text-gray-600 mt-0.5" />
                  <div className="flex-1">
                    <span className="text-gray-600 block mb-1">이상행동 유형</span>
                    <span className="font-medium">{anomalyTypeLabels[event.type]}</span>
                  </div>
                </div>
                <div className="flex items-start gap-2">
                  <Clock className="w-4 h-4 text-gray-600 mt-0.5" />
                  <div className="flex-1">
                    <span className="text-gray-600 block mb-1">감지 시간</span>
                    <span className="font-medium">
                      {event.timestamp.toLocaleString('ko-KR', {
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                        second: '2-digit',
                      })}
                    </span>
                  </div>
                </div>
                <div className="flex items-start gap-2">
                  <MapPin className="w-4 h-4 text-gray-600 mt-0.5" />
                  <div className="flex-1">
                    <span className="text-gray-600 block mb-1">위치</span>
                    <span className="font-medium">{event.location}</span>
                  </div>
                </div>
              </div>
            </Card>

            <Card className="p-4 bg-gray-50">
              <h3 className="text-sm font-semibold text-gray-900 mb-3">카메라 정보</h3>
              <div className="space-y-3 text-sm">
                <div className="flex items-start gap-2">
                  <Camera className="w-4 h-4 text-gray-600 mt-0.5" />
                  <div className="flex-1">
                    <span className="text-gray-600 block mb-1">카메라명</span>
                    <span className="font-medium">{event.cameraName}</span>
                  </div>
                </div>
                <div className="flex items-start gap-2">
                  <User className="w-4 h-4 text-gray-600 mt-0.5" />
                  <div className="flex-1">
                    <span className="text-gray-600 block mb-1">카메라 ID</span>
                    <span className="font-mono font-medium">{event.cameraId}</span>
                  </div>
                </div>
              </div>
            </Card>
          </div>

          <Card className="p-4">
            <h3 className="text-sm font-semibold text-gray-900 mb-3">AI 분석 결과</h3>
            <div className="space-y-3">
              <div>
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm text-gray-600">신뢰도</span>
                  <span className="text-sm font-semibold text-gray-900">{event.confidence}%</span>
                </div>
                <div className="w-full h-3 bg-gray-200 rounded-full overflow-hidden">
                  <div
                    className={`h-full transition-all ${
                      event.confidence >= 80 ? 'bg-green-500' :
                      event.confidence >= 60 ? 'bg-orange-500' :
                      'bg-red-500'
                    }`}
                    style={{ width: `${event.confidence}%` }}
                  />
                </div>
              </div>

              <div className="flex items-center justify-between text-sm">
                <span className="text-gray-600">위험도</span>
                {getSeverityBadge()}
              </div>
            </div>
          </Card>

          {event.status === 'resolved' && (
            <Card className="p-4 bg-green-50 border-green-200">
              <div className="flex items-start gap-3">
                <CheckCircle className="w-5 h-5 text-green-600 mt-0.5" />
                <div>
                  <h3 className="text-sm font-semibold text-green-900 mb-1">처리 완료</h3>
                  <p className="text-xs text-green-700">
                    {event.resolvedBy && `담당자: ${event.resolvedBy}`}
                    {event.resolvedAt && ` • ${event.resolvedAt.toLocaleString('ko-KR')}`}
                  </p>
                </div>
              </div>
            </Card>
          )}

          {showNoteInput && (
            <Card className="p-4 bg-blue-50 border-blue-200">
              <h3 className="text-sm font-semibold text-blue-900 mb-2">메모 추가</h3>
              <textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="이 이상상황에 대한 메모를 입력하세요..."
                className="w-full px-3 py-2 border border-blue-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none resize-none"
                rows={3}
              />
              <div className="flex gap-2 mt-2">
                <Button
                  size="sm"
                  className="bg-blue-600 hover:bg-blue-700"
                  onClick={handleAddNote}
                  disabled={!note.trim()}
                >
                  저장
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setShowNoteInput(false);
                    setNote('');
                  }}
                >
                  취소
                </Button>
              </div>
            </Card>
          )}

          {canResolve && (
            <div className="space-y-3">
              {event.status !== 'resolved' && (
                <>
                  <div className="flex gap-2">
                    <Button
                      className="flex-1 bg-green-600 hover:bg-green-700 gap-2"
                      onClick={() => onStatusChange?.('resolved')}
                    >
                      <CheckCircle className="w-4 h-4" />
                      처리 완료
                    </Button>
                    {event.status === 'active' && (
                      <Button
                        variant="outline"
                        className="flex-1 border-orange-300 text-orange-700 hover:bg-orange-50"
                        onClick={() => onStatusChange?.('reviewing')}
                      >
                        <Eye className="w-4 h-4 mr-2" />
                        검토중으로 변경
                      </Button>
                    )}
                  </div>

                  <div className="flex gap-2">
                    <Button
                      variant="outline"
                      className="flex-1 border-red-300 text-red-700 hover:bg-red-50"
                      onClick={() => {
                        if (confirm('이 이상상황을 오탐(False Positive)으로 표시하시겠습니까?')) {
                          onStatusChange?.('resolved');
                          alert('오탐으로 처리되었습니다.');
                        }
                      }}
                    >
                      <XCircle className="w-4 h-4 mr-2" />
                      오탐 처리
                    </Button>
                    <Button
                      variant="outline"
                      className="flex-1"
                      onClick={() => setShowNoteInput(!showNoteInput)}
                    >
                      <MessageSquare className="w-4 h-4 mr-2" />
                      메모 추가
                    </Button>
                  </div>
                </>
              )}

              <div className="flex gap-2">
                <Button
                  variant="outline"
                  className="flex-1"
                  onClick={handleDownloadClip}
                >
                  <Download className="w-4 h-4 mr-2" />
                  영상 클립 다운로드
                </Button>
                <Button
                  variant="outline"
                  className="flex-1"
                  onClick={handleShareEvent}
                >
                  <Share2 className="w-4 h-4 mr-2" />
                  공유
                </Button>
              </div>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}