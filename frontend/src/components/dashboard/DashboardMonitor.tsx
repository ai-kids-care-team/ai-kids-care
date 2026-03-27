'use client';

import { useState, useEffect } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useAppSelector, useAppDispatch } from '@/store/hook';
import { CCTVGrid } from '@/components/monitoring/CCTVGrid';
import { CameraDetailModal } from '@/components/monitoring/CameraDetailModal';
import { FullscreenView } from '@/components/monitoring/FullscreenView';
import { useGetCamerasQuery } from '@/services/apis/camera.api';
import { useGetEventsQuery, useUpdateEventStatusMutation } from '@/services/apis/event.api';
import { DetectionEventsDetailModal } from '@/components/detectionEvents/DetectionEventsDetailModal';
import { setCredentials } from '@/store/slices/userSlice';
import { RightPanel } from '@/components/monitoring/RightPanel';
import { Sidebar } from '@/layout/Sidebar';
import { Button } from '@/components/shared/ui/button';
import { Card } from '@/components/shared/ui/card';
import type { Camera, AnomalyEvent } from '@/types/anomaly';
import { anomalyTypeLabels } from '@/types/anomaly';
import type { UserRole } from '@/types/user-role';
import { rolePermissions } from '@/types/user-role';
import { openLoginModal } from '@/utils/auth-modal';

const KINDERGARTEN_ID = '1';

// 원본 그대로 유지된 하이브리드 모드용 가짜 데이터
const allCameras: Camera[] = [
  { id: 'CAM-001', name: '정문 입구', location: '1층 현관', status: 'online', isRecording: true, category: 'entrance' },
  { id: 'CAM-002', name: '놀이터', location: '야외 놀이공간', status: 'online', isRecording: true, category: 'playground' },
  { id: 'CAM-003', name: '햇살반 교실', location: '2층 201호', status: 'online', isRecording: true, category: 'classroom', assignedTeacher: 'teacher1' },
  { id: 'CAM-004', name: '무지개반 교실', location: '2층 202호', status: 'online', isRecording: true, category: 'classroom', assignedTeacher: 'teacher2' },
  { id: 'CAM-005', name: '별님반 교실', location: '3층 301호', status: 'online', isRecording: true, category: 'classroom', assignedTeacher: 'teacher3' },
  { id: 'CAM-006', name: '복도 A', location: '2층 중앙복도', status: 'online', isRecording: true, category: 'corridor' },
  { id: 'CAM-007', name: '복도 B', location: '3층 중앙복도', status: 'online', isRecording: true, category: 'corridor' },
  { id: 'CAM-008', name: '급식실', location: '1층 식당', status: 'online', isRecording: true, category: 'office' },
  { id: 'CAM-009', name: '운동장', location: '야외 운동공간', status: 'online', isRecording: true, category: 'playground' },
  { id: 'CAM-010', name: '후문', location: '1층 후문', status: 'online', isRecording: true, category: 'entrance' },
  { id: 'CAM-011', name: '주차장', location: '지상 주차장', status: 'online', isRecording: true, category: 'parking' },
  { id: 'CAM-012', name: '사무실', location: '1층 행정실', status: 'online', isRecording: true, category: 'office' },
];

const generateMockEvents = (): AnomalyEvent[] => {
  return ([
    { id: 'EVT-001', timestamp: new Date(Date.now() - 2 * 60000), cameraId: 'CAM-001', cameraName: '정문 입구', type: 'Trespass', confidence: 95, location: '1층 현관', status: 'active', severity: 'high' },
    { id: 'EVT-002', timestamp: new Date(Date.now() - 15 * 60000), cameraId: 'CAM-009', cameraName: '운동장', type: 'Fight', confidence: 92, location: '야외 운동공간', status: 'reviewing', severity: 'high' },
    { id: 'EVT-003', timestamp: new Date(Date.now() - 45 * 60000), cameraId: 'CAM-002', cameraName: '놀이터', type: 'Wander', confidence: 78, location: '야외 놀이공간', status: 'resolved', severity: 'low' }
  ] as AnomalyEvent[]).sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
};


export function DashboardMonitor() {
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.user);
  const currentRole: UserRole = user?.role ?? 'GUARDIAN';

  // 인증 대기 상태
  const [isAuthChecking, setIsAuthChecking] = useState(true);

  // 💡 [수정된 부분] 새로고침 시 LocalStorage에서 유저/토큰 정보 복구
  useEffect(() => {
    // 1. 이미 Redux 스토어에 유저가 존재하면 로딩 즉시 해제
    if (user) {
      setIsAuthChecking(false);
      return;
    }

    // 2. Redux에 유저가 없다면 (새로고침 직후) LocalStorage 확인
    const storedUser = localStorage.getItem('user');
    const storedToken = localStorage.getItem('token');

    // 💡 [수정] storedUser와 storedToken이 모두 존재할 때만 실행하도록 변경
    if (storedUser && storedToken) {
      try {
        const parsedUser = JSON.parse(storedUser);
        dispatch(setCredentials({ user: parsedUser, token: storedToken }));
      } catch (error) {
        console.error("로컬스토리지 데이터 파싱 실패", error);
        // 데이터가 손상되었다면 비워버림
        localStorage.removeItem('user');
        localStorage.removeItem('token');
      }
    }

    // 복구 시도가 모두 끝났으므로 로딩 해제
    setIsAuthChecking(false);
  }, [user, dispatch]);
  // ---------------------------------------------------------

  // RTK Query를 이용한 10초 주기 실시간 자동 갱신
  const { data: serverCameras, isError: isCameraError } = useGetCamerasQuery(KINDERGARTEN_ID, { skip: !user });
  const { data: serverEvents, isError: isEventError } = useGetEventsQuery({ kindergartenId: KINDERGARTEN_ID }, { skip: !user, pollingInterval: 10000 });
  const [updateEventStatus] = useUpdateEventStatusMutation();

  const [localEvents, setLocalEvents] = useState<AnomalyEvent[]>([]);
  const [filteredCameras, setFilteredCameras] = useState<Camera[]>([]);
  const [layout, setLayout] = useState<'1x1' | '2x2' | '3x3'>('2x2');
  const [isRecording, setIsRecording] = useState(true);
  const [selectedCamera, setSelectedCamera] = useState<Camera | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<AnomalyEvent | null>(null);
  const [fullscreenCamera, setFullscreenCamera] = useState<Camera | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<'all' | Camera['category'] | 'parking'>('all');
  const [currentPage, setCurrentPage] = useState(1);
  /** 빠른 작업에서 제어할 카메라 (선택된 카메라만 전체화면/일시정지/다운로드 적용) */
  const [quickActionCameraId, setQuickActionCameraId] = useState<string | null>(null);
  /** 카메라별 일시정지 상태 (Set에 들어 있으면 해당 카메라 일시정지) */
  const [pausedCameraIds, setPausedCameraIds] = useState<Set<string>>(new Set());

  // 카메라 데이터 권한 필터링 및 하이브리드 적용
  useEffect(() => {
    const permissions = rolePermissions[currentRole];
    let sourceCameras = isCameraError || !serverCameras ? allCameras : serverCameras;

    if (!permissions.canViewAllCameras && permissions.canViewOwnClassroom) {
      if (currentRole === 'TEACHER') sourceCameras = sourceCameras.filter(c => c.category === 'classroom' && c.assignedTeacher === 'teacher1');
      else if (currentRole === 'GUARDIAN') sourceCameras = sourceCameras.filter(c => c.category === 'classroom' || c.category === 'playground');
    }

    if (categoryFilter === 'all') setFilteredCameras(sourceCameras);
    else if (categoryFilter === 'office') setFilteredCameras(sourceCameras.filter(c => c.category === 'office' || c.category === 'parking'));
    else setFilteredCameras(sourceCameras.filter(c => c.category === categoryFilter));

    setCurrentPage(1);
  }, [currentRole, serverCameras, isCameraError, categoryFilter]);

  // 빠른 작업 카메라 선택 동기화: 목록이 바뀌면 선택이 없거나 목록에 없으면 첫 번째로
  useEffect(() => {
    if (filteredCameras.length === 0) {
      setQuickActionCameraId(null);
      return;
    }
    const exists = quickActionCameraId && filteredCameras.some((c) => c.id === quickActionCameraId);
    if (!exists) setQuickActionCameraId(filteredCameras[0].id);
  }, [filteredCameras, quickActionCameraId]);

  // 이벤트 데이터 하이브리드 적용
  useEffect(() => {
    if (isEventError || !serverEvents) {
      setLocalEvents(prev => prev.length === 0 ? generateMockEvents() : prev);
    } else {
      setLocalEvents(serverEvents);
    }
  }, [serverEvents, isEventError]);

  // 💡 [수정된 부분] 로딩 및 비로그인 상태 UI 처리
  if (isAuthChecking) {
    return <div className="h-screen flex items-center justify-center">Loading...</div>;
  }

  if (!user) {
    return (
        <div className="h-screen flex flex-col items-center justify-center bg-gray-50 gap-4">
          <div className="text-center">
            <h2 className="text-xl font-bold text-gray-800 mb-2">로그인이 필요합니다</h2>
            <p className="text-sm text-gray-500">세션이 만료되었거나 로그인 정보가 없습니다.</p>
          </div>
          <Button onClick={openLoginModal}>로그인</Button>
        </div>
    );
  }

  const permissions = rolePermissions[currentRole];

  // UI 핸들러들
  const handleEventStatusChange = async (status: AnomalyEvent['status']) => {
    if (selectedEvent) {
      // 낙관적 업데이트: 사용자 경험을 위해 API 응답 대기 없이 UI를 먼저 즉시 반영
      setLocalEvents(prev => prev.map(e => e.id === selectedEvent.id ? { ...e, status } : e));
      setSelectedEvent(prev => prev ? { ...prev, status } : null);

      try {
        if (!selectedEvent.id.includes('EVT')) {
          await updateEventStatus({ kindergartenId: KINDERGARTEN_ID, eventId: selectedEvent.id, status }).unwrap();
        }
      } catch (error) {
        console.warn('API 상태 업데이트 실패. 로컬 화면만 반영됩니다.');
      }
    }
  };

  const handleAddNote = (note: string) => {
    alert(`해당 이상상황에 메모가 저장되었습니다: ${note}`);
  };

  const cameraStats = {
    total: filteredCameras.length,
    online: filteredCameras.filter(c => c.status === 'online').length,
    offline: filteredCameras.filter(c => c.status === 'offline').length
  };

  const getCamerasPerPage = () => layout === '1x1' ? 1 : layout === '2x2' ? 4 : 9;
  const camerasPerPage = getCamerasPerPage();
  const totalPages = Math.ceil(filteredCameras.length / camerasPerPage);
  const startIndex = (currentPage - 1) * camerasPerPage;
  // 녹화 제어(전체) 반영: 전역 isRecording으로 카드/모달/전체화면 표시 통일
  const displayedCameras = filteredCameras
    .slice(startIndex, startIndex + camerasPerPage)
    .map((c) => ({ ...c, isRecording: isRecording }));

  return (
      <>
        <div className="h-screen flex flex-col bg-gray-50">
          <div className="flex-1 flex overflow-hidden">
            <Sidebar
                currentRole={currentRole}
                userName={user.name}
                cameraStats={cameraStats}
                onCategoryFilter={setCategoryFilter}
                currentCategory={categoryFilter}
            />

            <main className="flex-1 min-h-0 p-6 overflow-y-auto overflow-x-hidden">
              <div className="mb-4 flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-bold text-gray-900">실시간 모니터링</h2>
                  <p className="text-sm text-gray-500">
                    전체 {filteredCameras.length}개 중 {startIndex + 1}-{Math.min(startIndex + camerasPerPage, filteredCameras.length)}번째 • {layout}
                    {pausedCameraIds.size > 0 && (
                      <> • 일시정지: {Array.from(pausedCameraIds)
                        .map((id) => filteredCameras.find((c) => c.id === id)?.name ?? id)
                        .filter(Boolean)
                        .join(', ')}
                      </>
                    )}
                  </p>
                </div>

                {totalPages > 1 && (
                    <div className="flex items-center gap-2">
                      <Button variant="outline" size="sm" onClick={() => setCurrentPage((p) => Math.max(1, p - 1))} disabled={currentPage === 1}>
                        <ChevronLeft className="h-4 w-4" />
                      </Button>
                      <span className="text-sm font-medium text-gray-600 w-12 text-center">
                    {currentPage} / {totalPages}
                  </span>
                      <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                          disabled={currentPage === totalPages}
                      >
                        <ChevronRight className="h-4 w-4" />
                      </Button>
                    </div>
                )}
              </div>

              <div className="mb-6">
                <CCTVGrid
                    cameras={displayedCameras}
                    events={localEvents}
                    onCameraSelect={(id) => setSelectedCamera(filteredCameras.find(c => c.id === id) || null)}
                    onCameraFullscreen={(id) => setFullscreenCamera(filteredCameras.find(c => c.id === id) || null)}
                    onEventClick={(id) => setSelectedEvent(localEvents.find(e => e.id === id) || null)}
                    layout={layout}
                    pausedCameraIds={pausedCameraIds}
                />
              </div>

              {/* 카메라 아래: 검토 전/검토 중 이상상황 리스트 */}
              <Card className="p-4 mb-6">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-semibold text-gray-900">이상상황 알림 리스트</h3>
                  <span className="text-xs text-gray-500">
                    {localEvents.filter(e => e.status !== 'resolved').length}건
                  </span>
                </div>

                <div className="space-y-2">
                  {localEvents.filter(e => e.status !== 'resolved').length === 0 ? (
                    <div className="text-sm text-gray-500 py-6 text-center">
                      검토 전/검토 중인 이상상황이 없습니다.
                    </div>
                  ) : (
                    localEvents
                      .filter(e => e.status !== 'resolved')
                      .map((event) => (
                        <button
                          key={event.id}
                          type="button"
                          onClick={() => setSelectedEvent(event)}
                          className="w-full text-left px-3 py-2 rounded border border-gray-200 hover:bg-gray-50 transition-colors"
                        >
                          <div className="flex items-center justify-between gap-3">
                            <div className="min-w-0">
                              <div className="text-sm font-medium text-gray-900 truncate">
                                {event.cameraName}
                              </div>
                              <div className="text-xs text-gray-600 mt-0.5">
                                {anomalyTypeLabels[event.type]} • {event.confidence}%
                              </div>
                            </div>

                            <div className="shrink-0 text-right">
                              <div className="text-xs font-medium text-orange-600">
                                {event.status === 'active' ? '검토 전' : '검토 중'}
                              </div>
                              <div className="text-[11px] text-gray-400 mt-0.5">
                                {new Date(event.timestamp).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}
                              </div>
                            </div>
                          </div>
                        </button>
                      ))
                  )}
                </div>
              </Card>

              {/*<div className="border-t border-gray-200 pt-8 mt-4">*/}
              {/* <SystemMetrics metrics={metrics} isError={isMetricsError} />*/}
              {/*</div>*/}
            </main>

            <RightPanel
                events={localEvents}
                onEventClick={(id) => setSelectedEvent(localEvents.find(e => e.id === id) || null)}
                currentRole={currentRole}
                onLayoutChange={setLayout}
                currentLayout={layout}
                isRecording={isRecording}
                onRecordingToggle={() => setIsRecording(!isRecording)}
                cameras={filteredCameras}
                selectedCameraId={quickActionCameraId}
                onSelectCameraId={setQuickActionCameraId}
                onQuickFullscreen={() => {
                  const cam = quickActionCameraId ? filteredCameras.find((c) => c.id === quickActionCameraId) : null;
                  if (cam) setFullscreenCamera(cam);
                }}
                onQuickPause={() => {
                  if (quickActionCameraId) {
                    setPausedCameraIds((prev) => {
                      const next = new Set(prev);
                      if (next.has(quickActionCameraId)) next.delete(quickActionCameraId);
                      else next.add(quickActionCameraId);
                      return next;
                    });
                  }
                }}
                onQuickDownload={() => {
                  const cam = quickActionCameraId ? filteredCameras.find((c) => c.id === quickActionCameraId) : null;
                  if (cam?.streamUrl) window.open(cam.streamUrl, '_blank');
                  else alert('이 카메라는 스트림 URL이 없습니다.');
                }}
                isVideoPaused={quickActionCameraId ? pausedCameraIds.has(quickActionCameraId) : false}
            />
          </div>
        </div>

        {selectedCamera && (
            <CameraDetailModal
                camera={{ ...selectedCamera, isRecording }}
                onClose={() => setSelectedCamera(null)}
                onFullscreen={() => {
                  setFullscreenCamera(selectedCamera);
                  setSelectedCamera(null);
                }}
            />
        )}

        {selectedEvent && (
            <DetectionEventsDetailModal
                event={selectedEvent}
                onClose={() => setSelectedEvent(null)}
                onStatusChange={handleEventStatusChange}
                onAddNote={handleAddNote}
                canResolve={permissions.canResolveAnomaly}
            />
        )}

        {fullscreenCamera && (
            <FullscreenView
                camera={{ ...fullscreenCamera, isRecording }}
                onClose={() => setFullscreenCamera(null)}
            />
        )}
      </>
  );
}