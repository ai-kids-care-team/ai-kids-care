import { useState } from 'react';
import {
  AlertTriangle,
  CheckCircle,
  Clock,
  Eye,
  MapPin,
  Download,
  Grid2x2,
  Grid3x3,
  Square,
  Video,
  VideoOff,
  Play,
  Pause,
  Circle
} from 'lucide-react';
import { Card } from '@/components/shared/ui/card';
import { Badge } from '@/components/shared/ui/badge';
import { Button } from '@/components/shared/ui/button';
import { ScrollArea } from '@/components/shared/ui/scroll-area';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/shared/ui/tabs';
import type { AnomalyEvent, UserRole } from '../../types/anomaly';
import { anomalyTypeLabels, anomalyTypeColors, rolePermissions } from '../../types/anomaly';

interface RightPanelProps {
  events: AnomalyEvent[];
  onEventClick?: (eventId: string) => void;
  currentRole: UserRole;
  onLayoutChange?: (layout: '1x1' | '2x2' | '3x3') => void;
  currentLayout: '1x1' | '2x2' | '3x3';
  isRecording: boolean;
  onRecordingToggle: () => void;
  onQuickFullscreen?: () => void;
  onQuickPause?: () => void;
  onQuickDownload?: () => void;
  isVideoPaused?: boolean;
}

export function RightPanel({
                             events,
                             onEventClick,
                             currentRole,
                             onLayoutChange,
                             currentLayout,
                             isRecording,
                             onRecordingToggle,
                             onQuickFullscreen,
                             onQuickPause,
                             onQuickDownload,
                             isVideoPaused = false
                           }: RightPanelProps) {
  const permissions = rolePermissions[currentRole];
  const [activeTab, setActiveTab] = useState('anomaly');

  const getStatusIcon = (status: AnomalyEvent['status']) => {
    switch (status) {
      case 'active':
        return <AlertTriangle className="w-4 h-4 text-red-500" />;
      case 'reviewing':
        return <Clock className="w-4 h-4 text-orange-500" />;
      case 'resolved':
        return <CheckCircle className="w-4 h-4 text-green-500" />;
    }
  };

  const getStatusLabel = (status: AnomalyEvent['status']) => {
    switch (status) {
      case 'active':
        return '긴급';
      case 'reviewing':
        return '검토중';
      case 'resolved':
        return '처리완료';
    }
  };

  const getStatusColor = (status: AnomalyEvent['status']) => {
    switch (status) {
      case 'active':
        return 'bg-red-100 text-red-700 border-red-200';
      case 'reviewing':
        return 'bg-orange-100 text-orange-700 border-orange-200';
      case 'resolved':
        return 'bg-green-100 text-green-700 border-green-200';
    }
  };

  // 💡 [버그 픽스] API에서 온 문자열이든 Date 객체든 안전하게 파싱하도록 수정
  const formatTime = (dateInput: Date | string) => {
    const date = new Date(dateInput);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);

    if (minutes < 1) return '방금 전';
    if (minutes < 60) return `${minutes}분 전`;

    return date.toLocaleTimeString('ko-KR', {
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  return (
      // 💡 [버그 픽스] min-h-0 추가하여 컨테이너 밖으로 스크롤이 밀려나지 않게 제어
      <div className="w-80 bg-white border-l border-gray-200 h-full flex flex-col min-h-0">
        <Tabs value={activeTab} onValueChange={setActiveTab} className="flex-1 flex flex-col min-h-0">
          <div className="border-b border-gray-200 shrink-0">
            <TabsList className="w-full grid grid-cols-2 rounded-none h-auto bg-transparent">
              <TabsTrigger
                  value="anomaly"
                  className="rounded-none data-[state=active]:border-b-2 data-[state=active]:border-purple-600"
              >
                이상상황
              </TabsTrigger>
              <TabsTrigger
                  value="control"
                  className="rounded-none data-[state=active]:border-b-2 data-[state=active]:border-purple-600"
              >
                제어
              </TabsTrigger>
            </TabsList>
          </div>

          {/* 💡 [버그 픽스] data-[state=active]:flex 를 통해 활성화 시 블록이 아닌 플렉스로 변경 및 min-h-0 추가 */}
          <TabsContent value="anomaly" className="data-[state=active]:flex flex-col flex-1 min-h-0 m-0">
            {/* Anomaly Log Header */}
            <div className="p-4 border-b border-gray-200 shrink-0">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <AlertTriangle className="w-5 h-5 text-orange-600" />
                  <h2 className="font-semibold text-sm text-gray-900">AI 알림로그</h2>
                </div>
                <Badge variant="secondary" className="bg-orange-100 text-orange-700">
                  {events.filter(e => e.status === 'active').length}건
                </Badge>
              </div>
              <p className="text-xs text-gray-500">실시간 이상행동 탐지</p>
            </div>

            {/* Anomaly Events List */}
            {/* 💡 [버그 픽스] ScrollArea 대신 overflow-y-auto 사용으로 확실한 스크롤 보장 */}
            <div className="flex-1 overflow-y-auto p-4 min-h-0">
              <div className="space-y-3">
                {events.length === 0 ? (
                    <div className="text-center py-8">
                      <CheckCircle className="w-12 h-12 text-green-500 mx-auto mb-3" />
                      <p className="text-sm text-gray-600">이상상황이 감지되지 않았습니다</p>
                      <p className="text-xs text-gray-500 mt-1">모든 구역이 안전합니다</p>
                    </div>
                ) : (
                    events.map((event) => (
                        <Card
                            key={event.id}
                            className={`p-3 cursor-pointer hover:shadow-md transition-shadow border-l-4 ${
                                event.status === 'active' ? 'border-l-red-500 bg-red-50/30' :
                                    event.status === 'reviewing' ? 'border-l-orange-500' :
                                        'border-l-green-500'
                            }`}
                            onClick={() => onEventClick?.(event.id)}
                        >
                          <div className="flex items-start justify-between mb-2">
                            <div className="flex items-center gap-1.5">
                              {getStatusIcon(event.status)}
                              <Badge className={`${getStatusColor(event.status)} text-xs px-1.5 py-0`}>
                                {getStatusLabel(event.status)}
                              </Badge>
                            </div>
                            <span className="text-xs text-gray-500">
                        {formatTime(event.timestamp)}
                      </span>
                          </div>

                          <div className="mb-2">
                            <div className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded ${anomalyTypeColors[event.type]} text-white text-xs font-medium mb-1.5`}>
                              <AlertTriangle className="w-3 h-3" />
                              {anomalyTypeLabels[event.type]}
                            </div>
                            <p className="font-medium text-gray-900 text-sm">
                              {event.cameraName}
                            </p>
                          </div>

                          <div className="space-y-1.5 mb-2">
                            <div className="flex items-center gap-2 text-xs text-gray-600">
                              <MapPin className="w-3 h-3" />
                              <span>{event.location}</span>
                            </div>
                            <div className="flex items-center justify-between text-xs">
                              <span className="text-gray-600">신뢰도</span>
                              <div className="flex items-center gap-2">
                                <div className="w-16 h-1.5 bg-gray-200 rounded-full overflow-hidden">
                                  <div
                                      className={`h-full ${
                                          event.confidence >= 80 ? 'bg-green-500' :
                                              event.confidence >= 60 ? 'bg-orange-500' :
                                                  'bg-red-500'
                                      }`}
                                      style={{ width: `${event.confidence}%` }}
                                  />
                                </div>
                                <span className="font-medium text-gray-900 text-xs">
                            {event.confidence}%
                          </span>
                              </div>
                            </div>
                          </div>

                          {permissions.canResolveAnomaly && (
                              <Button
                                  variant="outline"
                                  size="sm"
                                  className="w-full gap-2 h-7 text-xs"
                                  onClick={(e: React.MouseEvent<HTMLButtonElement>) => {
                                    e.stopPropagation();
                                    onEventClick?.(event.id);
                                  }}
                              >
                                <Eye className="w-3 h-3" />
                                상세 보기
                              </Button>
                          )}
                        </Card>
                    ))
                )}
              </div>
            </div>

            {permissions.canExportReports && (
                <div className="p-4 border-t border-gray-200 shrink-0">
                  <Button variant="outline" className="w-full gap-2" size="sm">
                    <Download className="w-4 h-4" />
                    로그 내보내기
                  </Button>
                </div>
            )}
          </TabsContent>

          {/* 💡 [버그 픽스] 제어 탭도 동일하게 설정 */}
          <TabsContent value="control" className="data-[state=active]:flex flex-col flex-1 min-h-0 m-0">
            <div className="flex-1 overflow-y-auto p-4 min-h-0">
              <div className="space-y-4">
                {/* Recording Control */}
                <Card className="p-4">
                  <h3 className="text-sm font-semibold text-gray-900 mb-3">녹화 제어</h3>
                  <Button
                      className={`w-full gap-2 ${isRecording ? 'bg-red-600 hover:bg-red-700' : ''}`}
                      onClick={onRecordingToggle}
                  >
                    {isRecording ? (
                        <>
                          <Pause className="w-4 h-4" />
                          녹화 중지
                        </>
                    ) : (
                        <>
                          <Play className="w-4 h-4" />
                          전체 녹화 시작
                        </>
                    )}
                  </Button>
                  {isRecording && (
                      <div className="mt-3 p-2 bg-red-50 rounded border border-red-200">
                        <div className="flex items-center gap-2 text-xs text-red-700">
                          <Circle className="w-2 h-2 fill-red-500 text-red-500 animate-pulse" />
                          <span className="font-medium">녹화 진행 중</span>
                        </div>
                      </div>
                  )}
                </Card>

                {/* Screen Layout */}
                <Card className="p-4">
                  <h3 className="text-sm font-semibold text-gray-900 mb-3">화면 레이아웃</h3>
                  <div className="grid grid-cols-3 gap-2">
                    <Button
                        variant={currentLayout === '1x1' ? 'default' : 'outline'}
                        size="sm"
                        className="flex flex-col gap-1 h-auto py-2"
                        onClick={() => onLayoutChange?.('1x1')}
                    >
                      <Square className="w-5 h-5" />
                      <span className="text-xs">1×1</span>
                    </Button>
                    <Button
                        variant={currentLayout === '2x2' ? 'default' : 'outline'}
                        size="sm"
                        className="flex flex-col gap-1 h-auto py-2"
                        onClick={() => onLayoutChange?.('2x2')}
                    >
                      <Grid2x2 className="w-5 h-5" />
                      <span className="text-xs">2×2</span>
                    </Button>
                    <Button
                        variant={currentLayout === '3x3' ? 'default' : 'outline'}
                        size="sm"
                        className="flex flex-col gap-1 h-auto py-2"
                        onClick={() => onLayoutChange?.('3x3')}
                    >
                      <Grid3x3 className="w-5 h-5" />
                      <span className="text-xs">3×3</span>
                    </Button>
                  </div>
                </Card>

                {/* System Status (원복 완료!) */}
                {/*{permissions.canViewStatistics && (*/}
                {/*    <Card className="p-4">*/}
                {/*      <h3 className="text-sm font-semibold text-gray-900 mb-3">시스템 상태</h3>*/}
                {/*      <div className="space-y-2">*/}
                {/*        <div className="flex items-center justify-between text-sm">*/}
                {/*          <span className="text-gray-600">스토리지</span>*/}
                {/*          <span className="font-medium">62%</span>*/}
                {/*        </div>*/}
                {/*        <div className="w-full h-2 bg-gray-200 rounded-full overflow-hidden">*/}
                {/*          <div className="h-full bg-green-500" style={{ width: '62%' }} />*/}
                {/*        </div>*/}
                {/*        <div className="flex items-center justify-between text-sm mt-3">*/}
                {/*          <span className="text-gray-600">대역폭</span>*/}
                {/*          <span className="font-medium">45%</span>*/}
                {/*        </div>*/}
                {/*        <div className="w-full h-2 bg-gray-200 rounded-full overflow-hidden">*/}
                {/*          <div className="h-full bg-blue-500" style={{ width: '45%' }} />*/}
                {/*        </div>*/}
                {/*      </div>*/}
                {/*    </Card>*/}
                {/*)}*/}

                {/* Quick Actions */}
                <Card className="p-4">
                  <h3 className="text-sm font-semibold text-gray-900 mb-3">빠른 작업</h3>
                  <div className="space-y-2">
                    <Button
                        variant="outline"
                        size="sm"
                        className="w-full justify-start gap-2"
                        onClick={onQuickFullscreen}
                    >
                      <Video className="w-4 h-4" />
                      전체 화면 보기
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        className="w-full justify-start gap-2"
                        onClick={onQuickPause}
                    >
                      {isVideoPaused ? (
                          <>
                            <Play className="w-4 h-4" />
                            재생
                          </>
                      ) : (
                          <>
                            <Pause className="w-4 h-4" />
                            일시정지
                          </>
                      )}
                    </Button>
                    {permissions.canExportReports && (
                        <Button
                            variant="outline"
                            size="sm"
                            className="w-full justify-start gap-2"
                            onClick={onQuickDownload}
                        >
                          <Download className="w-4 h-4" />
                          영상 다운로드
                        </Button>
                    )}
                  </div>
                </Card>
              </div>
            </div>
          </TabsContent>
        </Tabs>
      </div>
  );
}