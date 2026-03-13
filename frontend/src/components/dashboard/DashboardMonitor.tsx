'use client';

import { useState, useEffect } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { SystemMetrics } from './SystemMetrics';
import { useAppSelector } from '@/store/hook';
import { CCTVGrid} from '@/components/monitoring/CCTVGrid';
import { CameraDetailModal} from '@/components/monitoring/CameraDetailModal';
import { FullscreenView } from '@/components/monitoring/FullscreenView';
import { useGetCamerasQuery} from '@/services/apis/camera.api';
import { useGetEventsQuery, useUpdateEventStatusMutation} from '@/services/apis/event.api';
import { EventDetailModal } from '@/components/events/EventDetailModal';
import { useGetDashboardMetricsQuery } from '@/services/apis/metrics.api';
import { RightPanel } from '@/components/monitoring/RightPanel';
import { Sidebar } from '@/layout/Sidebar';
import { Button } from '@/components/shared/ui/button';
import type { Camera, AnomalyEvent, AnomalyType, UserRole } from '@/types/anomaly';
import { rolePermissions } from '@/types/anomaly';

const KINDERGARTEN_ID = '1';

// 하이브리드 모드용 가짜 데이터 (API 실패 시 사용)
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

const anomalyTypes: AnomalyType[] = [
  'Assault', 'Fight', 'Burglary', 'Vandalism', 'Swoon', 'Wander', 'Trespass', 'Dump', 'Robbery', 'Datefight', 'Kidnap', 'Drunken'
];

const generateMockEvents = (): AnomalyEvent[] => {
  return ([
    { id: 'EVT-001', timestamp: new Date(Date.now() - 2 * 60000), cameraId: 'CAM-001', cameraName: '정문 입구', type: 'Trespass', confidence: 95, location: '1층 현관', status: 'active', severity: 'high' },
    { id: 'EVT-002', timestamp: new Date(Date.now() - 15 * 60000), cameraId: 'CAM-009', cameraName: '운동장', type: 'Fight', confidence: 92, location: '야외 운동공간', status: 'reviewing', severity: 'high' },
    { id: 'EVT-003', timestamp: new Date(Date.now() - 45 * 60000), cameraId: 'CAM-002', cameraName: '놀이터', type: 'Wander', confidence: 78, location: '야외 놀이공간', status: 'resolved', severity: 'low' }
  ] as AnomalyEvent[]).sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime());
};
export function DashboardMonitor() {
  const { user } = useAppSelector((state) => state.user);
  const currentRole: UserRole = (user?.role ?? 'guardian') as UserRole;
  const currentUserName = user?.name ?? '게스트';

  // RTK Query를 이용한 10초 주기 실시간 자동 갱신
  const { data: serverCameras, isError: isCameraError } = useGetCamerasQuery(KINDERGARTEN_ID, { skip: !user });
  const { data: serverEvents, isError: isEventError } = useGetEventsQuery({ kindergartenId: KINDERGARTEN_ID }, { skip: !user, pollingInterval: 10000 });
  //const { data: serverEvents, isError: isEventError } = useGetEventsQuery(KINDERGARTEN_ID, { skip: !user, pollingInterval: 10000 });
  const { data: metrics, isError: isMetricsError } = useGetDashboardMetricsQuery(undefined, { skip: !user, pollingInterval: 10000 });
  const [updateEventStatus] = useUpdateEventStatusMutation();

  const [localEvents, setLocalEvents] = useState<AnomalyEvent[]>([]);
  const [filteredCameras, setFilteredCameras] = useState<Camera[]>([]);
  const [layout, setLayout] = useState<'1x1' | '2x2' | '3x3'>('2x2');
  const [isRecording, setIsRecording] = useState(true);
  const [selectedCamera, setSelectedCamera] = useState<Camera | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<AnomalyEvent | null>(null);
  const [fullscreenCamera, setFullscreenCamera] = useState<Camera | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<'all' | Camera['category'] | 'parking'>('all');
  const [isVideoPaused, setIsVideoPaused] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);

  // 카메라 데이터 권한 필터링 및 하이브리드 적용
  useEffect(() => {
    const permissions = rolePermissions[currentRole];
    let sourceCameras = isCameraError || !serverCameras ? allCameras : serverCameras;

    if (!permissions.canViewAllCameras && permissions.canViewOwnClassroom) {
      if (currentRole === 'teacher') sourceCameras = sourceCameras.filter(c => c.category === 'classroom' && c.assignedTeacher === 'teacher1');
      else if (currentRole === 'guardian') sourceCameras = sourceCameras.filter(c => c.category === 'classroom' || c.category === 'playground');
    }

    if (categoryFilter === 'all') setFilteredCameras(sourceCameras);
    else if (categoryFilter === 'office') setFilteredCameras(sourceCameras.filter(c => c.category === 'office' || c.category === 'parking'));
    else setFilteredCameras(sourceCameras.filter(c => c.category === categoryFilter));

    setCurrentPage(1);
  }, [currentRole, serverCameras, isCameraError, categoryFilter]);

  // 이벤트 데이터 하이브리드 적용
  useEffect(() => {
    if (isEventError || !serverEvents) {
      setLocalEvents(prev => prev.length === 0 ? generateMockEvents() : prev);
    } else {
      setLocalEvents(serverEvents);
    }
  }, [serverEvents, isEventError]);

  const permissions = rolePermissions[currentRole];

  // UI 핸들러들
  const handleEventStatusChange = async (status: AnomalyEvent['status']) => {
    if (selectedEvent) {
      try {
        if (!selectedEvent.id.includes('MOCK')) {
          //await updateEventStatus({ eventId: selectedEvent.id, status }).unwrap();
          await updateEventStatus({ kindergartenId: KINDERGARTEN_ID, eventId: selectedEvent.id, status }).unwrap();
        }
      } catch (error) {
        console.warn('상태 업데이트 실패. 로컬 화면만 반영됩니다.');
      } finally {
        setLocalEvents(prev => prev.map(e => e.id === selectedEvent.id ? { ...e, status } : e));
        if (status === 'resolved') setSelectedEvent(null);
      }
    }
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
  const displayedCameras = filteredCameras.slice(startIndex, startIndex + camerasPerPage);

  return (
    <>
      <div className="h-screen flex flex-col bg-gray-50">
        
        <div className="flex-1 flex overflow-hidden">
          <main className="flex-1 p-6 overflow-auto">
            <div className="mb-4 flex items-center justify-between">
              <div>
                <h2 className="text-lg font-bold text-gray-900">실시간 모니터링</h2>
                <p className="text-sm text-gray-500">
                  전체 {filteredCameras.length}개 중 {startIndex + 1}-{Math.min(startIndex + camerasPerPage, filteredCameras.length)}번째 • {layout} {isVideoPaused && ' • 일시정지됨'}
                </p>
              </div>

              {totalPages > 1 && (
                <div className="flex items-center gap-2">
                  <Button variant="outline" size="sm" onClick={() => setCurrentPage(p => Math.max(1, p - 1))} disabled={currentPage === 1}><ChevronLeft className="h-4 w-4" /></Button>
                  <span className="text-sm font-medium text-gray-600 w-12 text-center">{currentPage} / {totalPages}</span>
                  <Button variant="outline" size="sm" onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))} disabled={currentPage === totalPages}><ChevronRight className="h-4 w-4" /></Button>
                </div>
              )}
            </div>

            <div className="mb-6">
              <CCTVGrid cameras={displayedCameras} onCameraSelect={(id) => setSelectedCamera(filteredCameras.find(c => c.id === id) || null)} onCameraFullscreen={(id) => setFullscreenCamera(filteredCameras.find(c => c.id === id) || null)} layout={layout} />
            </div>

            {/* 💡 기존의 길고 지저분했던 MetricGauge 함수와 렌더링 코드를 단 한 줄로 대체했습니다. */}
            <div className="border-t border-gray-200 pt-8 mt-4">
              <SystemMetrics metrics={metrics} isError={isMetricsError} />
            </div>

          </main>

          <RightPanel
            events={localEvents} onEventClick={(id) => setSelectedEvent(localEvents.find(e => e.id === id) || null)} currentRole={currentRole}
            onLayoutChange={setLayout} currentLayout={layout} isRecording={isRecording} onRecordingToggle={() => setIsRecording(!isRecording)}
            onQuickFullscreen={() => { if (filteredCameras.length > 0) setFullscreenCamera(filteredCameras[0]) }}
            onQuickPause={() => setIsVideoPaused(!isVideoPaused)} onQuickDownload={() => alert('다운로드')} isVideoPaused={isVideoPaused}
          />
        </div>
      </div>

      {selectedCamera && <CameraDetailModal camera={selectedCamera} onClose={() => setSelectedCamera(null)} onFullscreen={() => { setFullscreenCamera(selectedCamera); setSelectedCamera(null); }} />}
      {selectedEvent && <EventDetailModal event={selectedEvent} onClose={() => setSelectedEvent(null)} onStatusChange={handleEventStatusChange} onAddNote={(note) => alert(`메모: ${note}`)} canResolve={permissions.canResolveAnomaly} />}
      {fullscreenCamera && <FullscreenView camera={fullscreenCamera} onClose={() => setFullscreenCamera(null)} />}
    </>
  );
}