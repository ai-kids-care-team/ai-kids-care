'use client';
export const dynamic = 'force-dynamic';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/app/context/AuthContext';
import { apiClient } from '@/lib/apiClient';
import { DashboardMetric } from '@/app/types/api';
import { getDashboardMetrics } from '@/lib/apiClient';
// import { getDashboardMetrics, DashboardMetric } from '@/types/api';
import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts';
import { ChevronLeft, ChevronRight } from 'lucide-react';

import { TopBar } from '../components/TopBar';
import { Sidebar } from '../components/Sidebar';
import { CCTVGrid } from '../components/CCTVGrid';
import { RightPanel } from '../components/RightPanel';
import { CameraDetailModal } from '../components/CameraDetailModal';
import { EventDetailModal } from '../components/EventDetailModal';
import { FullscreenView } from '../components/FullscreenView';
import { Button } from '../components/ui/button';
import type { Camera, AnomalyEvent, AnomalyType } from '../types/anomaly';
import { rolePermissions } from '../types/anomaly';

const KINDERGARTEN_ID = '1';

// ==========================================
// 💡 백엔드 다운 시 보여줄 가짜(Mock) 데이터 (하이브리드 모드용)
// ==========================================
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
  'Assault', 'Fight', 'Burglary', 'Vandalism', 'Swoon',
  'Wander', 'Trespass', 'Dump', 'Robbery', 'Datefight',
  'Kidnap', 'Drunken'
];

const generateMockEvents = (): AnomalyEvent[] => {
  const events: AnomalyEvent[] = [
    { id: 'EVT-001', timestamp: new Date(Date.now() - 2 * 60000), cameraId: 'CAM-001', cameraName: '정문 입구', type: 'Trespass', confidence: 95, location: '1층 현관', status: 'active', severity: 'high' },
    { id: 'EVT-002', timestamp: new Date(Date.now() - 15 * 60000), cameraId: 'CAM-009', cameraName: '운동장', type: 'Fight', confidence: 92, location: '야외 운동공간', status: 'reviewing', severity: 'high' },
    { id: 'EVT-003', timestamp: new Date(Date.now() - 45 * 60000), cameraId: 'CAM-002', cameraName: '놀이터', type: 'Wander', confidence: 78, location: '야외 놀이공간', status: 'resolved', severity: 'low' }
  ];
  return events.sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime());
};

function MetricGauge({ metric }: { metric: DashboardMetric }) {
  const maxValue = metric.unit === '%' ? 100 : Math.max(metric.value * 1.5, 100);
  const value = Math.min(metric.value, maxValue);
  const percent = (value / maxValue) * 100;
  const data = [
    { name: 'value', value: percent, color: '#8b5cf6' },
    { name: 'empty', value: 100 - percent, color: '#e5e7eb' },
  ];

  return (
    <div className="bg-white rounded-xl p-4 border border-gray-200 shadow-sm">
      <p className="text-gray-500 font-medium text-sm mb-2">{metric.metricName}</p>
      <div className="h-32 relative">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={data} cx="50%" cy="70%" startAngle={180} endAngle={0} innerRadius="60%" outerRadius="100%" dataKey="value" stroke="none">
              {data.map((entry, index) => <Cell key={index} fill={entry.color} />)}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
        <p className="absolute bottom-2 left-0 right-0 text-center text-gray-900 font-bold text-lg">
          {metric.value} <span className="text-sm text-gray-500 font-normal">{metric.unit}</span>
        </p>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const router = useRouter();
  const { user, token, switchRole, isAuthenticated, isLoading } = useAuth();

  const [cameras, setCameras] = useState<Camera[]>([]);
  const [filteredCameras, setFilteredCameras] = useState<Camera[]>([]);
  const [events, setEvents] = useState<AnomalyEvent[]>([]);

  const [layout, setLayout] = useState<'1x1' | '2x2' | '3x3'>('2x2');
  const [isRecording, setIsRecording] = useState(true);
  const [selectedCamera, setSelectedCamera] = useState<Camera | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<AnomalyEvent | null>(null);
  const [fullscreenCamera, setFullscreenCamera] = useState<Camera | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<'all' | Camera['category'] | 'parking'>('all');
  const [isVideoPaused, setIsVideoPaused] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [metrics, setMetrics] = useState<DashboardMetric[]>([]);
  const [metricsError, setMetricsError] = useState('');

  // 1. 권한 및 로그인 체크
  useEffect(() => {
    if (!isAuthenticated && !isLoading) {
      router.push('/login');
    }
  }, [isAuthenticated, isLoading, router]);

  // 2. 메트릭 데이터 조회
  useEffect(() => {
    if (!token) return;
    getDashboardMetrics()
      .then(setMetrics)
      .catch(() => setMetricsError('메트릭 데이터를 불러오는데 실패했습니다.'));
  }, [token]);

  // 💡 3. 카메라 목록 불러오기 (하이브리드 모드 완벽 매핑)
  useEffect(() => {
    if (!user) return;

    const fetchCameras = async () => {
      let mappedCameras: Camera[] = [];

      try {
        const response = await apiClient.get(`/v1/kindergartens/${KINDERGARTEN_ID}/cameras`);
        const fetchedData = response.data.data || response.data;

        // 서버에 데이터가 아예 없다면 에러를 발생시켜 안전장치(Mock)로 넘김
        if (!fetchedData || fetchedData.length === 0) throw new Error('No Data');

        mappedCameras = fetchedData.map((cam: any) => {
          // 백엔드 데이터에 누락된 정보가 있다면 기존 allCameras 배열을 뒤져서 보완합니다.
          const fallbackCam = allCameras.find(c => c.id === cam.id);
          return {
            id: cam.id,
            name: cam.name || fallbackCam?.name || '알 수 없는 카메라',
            location: cam.location || fallbackCam?.location || '위치 미상',
            status: cam.status === 'ACTIVE' ? 'online' : 'offline',
            isRecording: cam.isRecording ?? true,
            category: fallbackCam?.category || 'classroom',
            assignedTeacher: fallbackCam?.assignedTeacher,
            streamUrl: cam.streamUrl
          };
        });
      } catch (error) {
        // 백엔드 연결 실패 시 기존의 완벽한 가짜 데이터를 그대로 띄움 (UI 깨짐 방지)
        mappedCameras = allCameras;
      }

      // 권한 필터링 (서버 데이터든 가짜 데이터든 동일하게 적용됨)
      const permissions = rolePermissions[user.role];
      let availableCameras = mappedCameras;

      if (!permissions.canViewAllCameras && permissions.canViewOwnClassroom) {
        if (user.role === 'teacher') {
          availableCameras = mappedCameras.filter(cam =>
              cam.category === 'classroom' && cam.assignedTeacher === 'teacher1'
          );
        } else if (user.role === 'guardian') {
          availableCameras = mappedCameras.filter(cam =>
              cam.category === 'classroom' || cam.category === 'playground'
          );
        }
      }

      setCameras(availableCameras);
      setFilteredCameras(availableCameras);
    };

    fetchCameras();
  }, [user]);

  // 💡 4. 이벤트 목록 불러오기 (하이브리드 모드 완벽 매핑)
  useEffect(() => {
    if (!user) return;

    const fetchEvents = async () => {
      try {
        const response = await apiClient.get(`/v1/kindergartens/${KINDERGARTEN_ID}/events`);
        const fetchedData = response.data.data || response.data;

        // 서버에 이벤트 데이터가 없다면 에러를 발생시켜 안전장치(Mock)로 넘김
        if (!fetchedData || fetchedData.length === 0) throw new Error('No Data');

        const mappedEvents: AnomalyEvent[] = fetchedData.map((evt: any) => {
          // 💡 핵심: 이벤트에 카메라 아이디만 올 경우, 카메라 배열을 뒤져서 이름과 위치를 찾아 결합
          const matchedCamera = allCameras.find(c => c.id === evt.cameraId);

          return {
            id: evt.id,
            timestamp: new Date(evt.detectedAt || evt.createdAt || Date.now()),
            cameraId: evt.cameraId || 'CAM-UNKNOWN',
            cameraName: evt.cameraName || matchedCamera?.name || `카메라 ${evt.cameraId}`,
            type: evt.eventType || evt.type || 'Wander',
            confidence: evt.confidenceScore || evt.confidence || 80, // 예측률
            location: evt.location || matchedCamera?.location || '위치 정보 누락', // 위치 정보 복구
            status: evt.isReviewed ? 'resolved' : (evt.status || 'active'), // 상태값 복구
            severity: evt.severity || (evt.confidenceScore > 90 ? 'high' : 'medium')
          };
        });

        mappedEvents.sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime());
        setEvents(mappedEvents);
      } catch (error) {
        // API 서버 연결 실패 시 기존의 완벽한 Mock Event 데이터를 생성하여 UI 유지
        setEvents(prev => {
          if (prev.length === 0) return generateMockEvents(); // 최초 로딩 시 기본 이벤트 3개 생성

          if (Math.random() < 0.3) {
            const randomCamera = allCameras[Math.floor(Math.random() * allCameras.length)];
            const randomType = anomalyTypes[Math.floor(Math.random() * anomalyTypes.length)];
            const newEvent: AnomalyEvent = {
              id: `EVT-MOCK-${Date.now()}`,
              timestamp: new Date(),
              cameraId: randomCamera.id,
              cameraName: randomCamera.name,
              type: randomType,
              confidence: Math.floor(Math.random() * 30) + 70,
              location: randomCamera.location,
              status: 'active',
              severity: Math.random() > 0.7 ? 'high' : Math.random() > 0.4 ? 'medium' : 'low'
            };
            return [newEvent, ...prev].slice(0, 10);
          }
          return prev;
        });
      }
    };

    fetchEvents();
    const interval = setInterval(fetchEvents, 10000);

    return () => clearInterval(interval);
  }, [user]);

  // 카테고리 필터링
  useEffect(() => {
    if (categoryFilter === 'all') {
      setFilteredCameras(cameras);
    } else if (categoryFilter === 'office') {
      setFilteredCameras(cameras.filter(cam => cam.category === 'office' || cam.category === 'parking'));
    } else {
      setFilteredCameras(cameras.filter(cam => cam.category === categoryFilter));
    }
  }, [categoryFilter, cameras]);

  useEffect(() => {
    setCurrentPage(1);
  }, [categoryFilter, layout]);

  // UI 핸들러들
  const handleCameraSelect = (cameraId: string) => {
    const camera = cameras.find(c => c.id === cameraId);
    if (camera) setSelectedCamera(camera);
  };

  const handleCameraFullscreen = (cameraId: string) => {
    const camera = cameras.find(c => c.id === cameraId);
    if (camera) setFullscreenCamera(camera);
  };

  const handleEventClick = (eventId: string) => {
    const event = events.find(e => e.id === eventId);
    if (event) setSelectedEvent(event);
  };

  const handleEventStatusChange = async (status: AnomalyEvent['status']) => {
    if (selectedEvent) {
      try {
        if (!selectedEvent.id.includes('MOCK')) {
          await apiClient.patch(`/v1/kindergartens/${KINDERGARTEN_ID}/events/${selectedEvent.id}`, { status });
        }
      } catch (error) {
        console.warn('API 상태 업데이트 실패. 로컬 화면만 반영됩니다.');
      } finally {
        setEvents(prev => prev.map(e =>
          e.id === selectedEvent.id
            ? {
                ...e,
                status,
                ...(status === 'resolved' ? { resolvedBy: user?.name, resolvedAt: new Date() } : {})
              }
            : e
        ));
        if (status === 'resolved') {
          setSelectedEvent(null);
        }
      }
    }
  };

  const handleEventAddNote = (note: string) => {
    alert(`메모가 추가되었습니다:\n"${note}"\n\n실제 환경에서는 데이터베이스에 저장됩니다.`);
  };

  const handleQuickFullscreen = () => { if (filteredCameras.length > 0) setFullscreenCamera(filteredCameras[0]); };
  const handleQuickPause = () => { setIsVideoPaused(!isVideoPaused); };
  const handleQuickDownload = () => { alert('선택된 카메라의 영상을 다운로드합니다.'); };
  const handleCategoryFilter = (category: typeof categoryFilter) => { setCategoryFilter(category); };

  const cameraStats = {
    total: cameras.length,
    online: cameras.filter(c => c.status === 'online').length,
    offline: cameras.filter(c => c.status === 'offline').length
  };

  const getCamerasPerPage = () => {
    if (layout === '1x1') return 1;
    if (layout === '2x2') return 4;
    return 9;
  };

  const camerasPerPage = getCamerasPerPage();
  const totalPages = Math.ceil(filteredCameras.length / camerasPerPage);
  const startIndex = (currentPage - 1) * camerasPerPage;
  const displayedCameras = filteredCameras.slice(startIndex, startIndex + camerasPerPage);

  if (isLoading || !user) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="flex flex-col items-center gap-4">
          <div className="w-8 h-8 border-4 border-purple-200 border-t-purple-600 rounded-full animate-spin"></div>
          <p className="text-gray-500 font-medium">데이터를 불러오는 중입니다...</p>
        </div>
      </div>
    );
  }

  const permissions = rolePermissions[user.role];

  return (
    <>
      <div className="h-screen flex flex-col bg-gray-50">
        <TopBar
            currentRole={user.role}
            username={user.name}
            onRoleChange={switchRole}
        />

        <div className="flex-1 flex overflow-hidden">
          <Sidebar
              currentRole={user.role}
              userName={user.name}
              cameraStats={cameraStats}
              onCategoryFilter={handleCategoryFilter}
              currentCategory={categoryFilter}
          />

          <main className="flex-1 p-6 overflow-auto">
            <div className="mb-4 flex items-center justify-between">
              <div>
                <h2 className="text-lg font-bold text-gray-900">실시간 모니터링</h2>
                <p className="text-sm text-gray-500">
                  전체 {filteredCameras.length}개 카메라 중 {startIndex + 1}-{Math.min(startIndex + camerasPerPage, filteredCameras.length)}번째 • {layout === '1x1' ? '1×1' : layout === '2x2' ? '2×2' : '3×3'} 레이아웃
                  {isVideoPaused && ' • 일시정지됨'}
                </p>
              </div>

              {totalPages > 1 && (
                <div className="flex items-center gap-2">
                  <Button variant="outline" size="sm" className="h-8 w-8 p-0" onClick={() => setCurrentPage(p => Math.max(1, p - 1))} disabled={currentPage === 1}>
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-sm font-medium text-gray-600 w-12 text-center">
                    {currentPage} / {totalPages}
                  </span>
                  <Button variant="outline" size="sm" className="h-8 w-8 p-0" onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))} disabled={currentPage === totalPages}>
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </div>

            <div className="mb-6">
              <CCTVGrid
                  cameras={displayedCameras}
                  onCameraSelect={handleCameraSelect}
                  onCameraFullscreen={handleCameraFullscreen}
                  layout={layout}
              />
            </div>

            <div className="border-t border-gray-200 pt-8 mt-4">
              {metricsError && (
                <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg text-red-600 text-sm">
                  {metricsError}
                </div>
              )}

              <section className="mb-8">
                <h2 className="text-lg font-bold text-gray-900 mb-4">시스템 리소스 현황</h2>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {metrics.slice(0, 6).map((metric, index) => (
                    <MetricGauge key={index} metric={metric} />
                  ))}
                </div>
              </section>

              <section className="mb-8">
                <h2 className="text-lg font-bold text-gray-900 mb-4">상세 데이터 그리드</h2>
                <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
                  <table className="w-full text-sm text-left">
                    <thead className="bg-gray-50 border-b border-gray-200 text-gray-600 font-medium">
                      <tr>
                        <th className="px-6 py-3">ID</th>
                        <th className="px-6 py-3">메트릭명</th>
                        <th className="px-6 py-3">값</th>
                        <th className="px-6 py-3">단위</th>
                        <th className="px-6 py-3">생성일시</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200">
                      {metrics.map((metric) => (
                        <tr key={metric.id} className="hover:bg-gray-50 transition-colors">
                          <td className="px-6 py-4 text-gray-500">{metric.id}</td>
                          <td className="px-6 py-4 font-medium text-gray-900">{metric.metricName}</td>
                          <td className="px-6 py-4 font-semibold text-purple-600">{metric.value}</td>
                          <td className="px-6 py-4 text-gray-500">{metric.unit}</td>
                          <td className="px-6 py-4 text-gray-400">
                            {new Date(metric.createdAt).toLocaleString('ko-KR')}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            </div>
          </main>

          <RightPanel
              events={events}
              onEventClick={handleEventClick}
              currentRole={user.role}
              onLayoutChange={setLayout}
              currentLayout={layout}
              isRecording={isRecording}
              onRecordingToggle={() => setIsRecording(!isRecording)}
              onQuickFullscreen={handleQuickFullscreen}
              onQuickPause={handleQuickPause}
              onQuickDownload={handleQuickDownload}
              isVideoPaused={isVideoPaused}
          />
        </div>
      </div>

      {selectedCamera && (
          <CameraDetailModal
              camera={selectedCamera}
              onClose={() => setSelectedCamera(null)}
              onFullscreen={() => {
                setFullscreenCamera(selectedCamera);
                setSelectedCamera(null);
              }}
          />
      )}

      {selectedEvent && (
          <EventDetailModal
              event={selectedEvent}
              onClose={() => setSelectedEvent(null)}
              onStatusChange={handleEventStatusChange}
              onAddNote={handleEventAddNote}
              canResolve={permissions.canResolveAnomaly}
          />
      )}

      {fullscreenCamera && (
          <FullscreenView
              camera={fullscreenCamera}
              onClose={() => setFullscreenCamera(null)}
          />
      )}
    </>
  );
}

// 'use client';
// export const dynamic = 'force-dynamic';
//
// import { useState, useEffect } from 'react';
// import { useRouter } from 'next/navigation';
// import { useAuth } from '@/app/context/AuthContext';
// import { apiClient } from '@/lib/apiClient'; // 👈 추가: 실제 API 통신 클라이언트
// import { DashboardMetric } from '@/types/api';
// import { getDashboardMetrics } from '@/lib/apiClient';
//// import { getDashboardMetrics, DashboardMetric } from '@/types/api';
// import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts';
// import { ChevronLeft, ChevronRight } from 'lucide-react';
//
// import { TopBar } from '../components/TopBar';
// import { Sidebar } from '../components/Sidebar';
// import { CCTVGrid } from '../components/CCTVGrid';
// import { RightPanel } from '../components/RightPanel';
// import { CameraDetailModal } from '../components/CameraDetailModal';
// import { EventDetailModal } from '../components/EventDetailModal';
// import { FullscreenView } from '../components/FullscreenView';
// import { Button } from '../components/ui/button';
// import type { Camera, AnomalyEvent } from '../types/anomaly';
// import { rolePermissions } from '../types/anomaly';
//
// // 임시 유치원 ID (실제로는 user 객체나 별도 전역 상태에서 가져와야 합니다)
// const KINDERGARTEN_ID = '1';
//
// // 💡 1. 가짜 데이터(allCameras, generateMockEvents, anomalyTypes)를 모두 삭제했습니다.
//
// function MetricGauge({ metric }: { metric: DashboardMetric }) {
//   // ... (기존 MetricGauge 코드 동일하게 유지)
//   const maxValue = metric.unit === '%' ? 100 : Math.max(metric.value * 1.5, 100);
//   const value = Math.min(metric.value, maxValue);
//   const percent = (value / maxValue) * 100;
//   const data = [
//     { name: 'value', value: percent, color: '#8b5cf6' },
//     { name: 'empty', value: 100 - percent, color: '#e5e7eb' },
//   ];
//
//   return (
//     <div className="bg-white rounded-xl p-4 border border-gray-200 shadow-sm">
//       <p className="text-gray-500 font-medium text-sm mb-2">{metric.metricName}</p>
//       <div className="h-32 relative">
//         <ResponsiveContainer width="100%" height="100%">
//           <PieChart>
//             <Pie data={data} cx="50%" cy="70%" startAngle={180} endAngle={0} innerRadius="60%" outerRadius="100%" dataKey="value" stroke="none">
//               {data.map((entry, index) => <Cell key={index} fill={entry.color} />)}
//             </Pie>
//           </PieChart>
//         </ResponsiveContainer>
//         <p className="absolute bottom-2 left-0 right-0 text-center text-gray-900 font-bold text-lg">
//           {metric.value} <span className="text-sm text-gray-500 font-normal">{metric.unit}</span>
//         </p>
//       </div>
//     </div>
//   );
// }
//
// export default function DashboardPage() {
//   const router = useRouter();
//   const { user, token, switchRole, isAuthenticated, isLoading } = useAuth();
//
//   // 상태를 빈 배열([])로 초기화합니다.
//   const [cameras, setCameras] = useState<Camera[]>([]);
//   const [filteredCameras, setFilteredCameras] = useState<Camera[]>([]);
//   const [events, setEvents] = useState<AnomalyEvent[]>([]);
//
//   const [layout, setLayout] = useState<'1x1' | '2x2' | '3x3'>('2x2');
//   const [isRecording, setIsRecording] = useState(true);
//   const [selectedCamera, setSelectedCamera] = useState<Camera | null>(null);
//   const [selectedEvent, setSelectedEvent] = useState<AnomalyEvent | null>(null);
//   const [fullscreenCamera, setFullscreenCamera] = useState<Camera | null>(null);
//   const [categoryFilter, setCategoryFilter] = useState<'all' | Camera['category'] | 'parking'>('all');
//   const [isVideoPaused, setIsVideoPaused] = useState(false);
//   const [currentPage, setCurrentPage] = useState(1);
//   const [metrics, setMetrics] = useState<DashboardMetric[]>([]);
//   const [metricsError, setMetricsError] = useState('');
//
//   // 권한 및 로그인 체크
//   useEffect(() => {
//     if (!isAuthenticated && !isLoading) {
//       router.push('/login');
//     }
//   }, [isAuthenticated, isLoading, router]);
//
//   // 메트릭 데이터 조회
//   useEffect(() => {
//     if (!token) return;
//     getDashboardMetrics(token)
//       .then(setMetrics)
//       .catch(() => setMetricsError('메트릭 데이터를 불러오는데 실패했습니다.'));
//   }, [token]);
//
//   // 💡 2. 실제 백엔드에서 카메라 목록을 불러오는 로직
//   useEffect(() => {
//     if (!user) return;
//
//     const fetchCameras = async () => {
//       try {
//         const response = await apiClient.get(`/v1/kindergartens/${KINDERGARTEN_ID}/cameras`);
//         const fetchedData = response.data.data || response.data; // API 응답 형태에 따라 조정
//
//         // 백엔드 데이터(API 스키마)를 프론트엔드 UI 컴포넌트(Camera 타입)에 맞게 변환 (매핑)
//         const mappedCameras: Camera[] = fetchedData.map((cam: any) => ({
//           id: cam.id,
//           name: cam.name,
//           location: cam.name, // location 데이터가 없다면 이름으로 대체
//           status: cam.status === 'ACTIVE' ? 'online' : 'offline',
//           isRecording: cam.isRecording,
//           category: 'classroom', // 실제 카테고리 정보가 서버에 있다면 그 값으로 대체
//           assignedTeacher: 'teacher1',
//         }));
//
//         // 권한에 따른 카메라 필터링 로직
//         const permissions = rolePermissions[user.role];
//         let availableCameras = mappedCameras;
//
//         if (!permissions.canViewAllCameras && permissions.canViewOwnClassroom) {
//           if (user.role === 'teacher') {
//             availableCameras = mappedCameras.filter(cam =>
//                 cam.category === 'classroom' && cam.assignedTeacher === 'teacher1'
//             );
//           } else if (user.role === 'guardian') {
//             availableCameras = mappedCameras.filter(cam =>
//                 cam.category === 'classroom' || cam.category === 'playground'
//             );
//           }
//         }
//
//         setCameras(availableCameras);
//         setFilteredCameras(availableCameras);
//       } catch (error) {
//         console.error('카메라 목록을 불러오지 못했습니다:', error);
//       }
//     };
//
//     fetchCameras();
//   }, [user]);
//
//   // 💡 3. 실제 백엔드에서 실시간 이벤트를 폴링(Polling) 방식으로 불러오는 로직
//   useEffect(() => {
//     if (!user) return;
//
//     const fetchEvents = async () => {
//       try {
//         const response = await apiClient.get(`/v1/kindergartens/${KINDERGARTEN_ID}/events`);
//         const fetchedData = response.data.data || response.data;
//
//         const mappedEvents: AnomalyEvent[] = fetchedData.map((evt: any) => ({
//           id: evt.id,
//           timestamp: new Date(evt.detectedAt || evt.createdAt),
//           cameraId: evt.cameraId,
//           cameraName: '카메라 ' + evt.cameraId, // 카메라 정보 조합 필요
//           type: evt.eventType || 'Wander',
//           confidence: evt.confidenceScore || 80,
//           location: '위치 정보',
//           status: evt.isReviewed ? 'resolved' : 'active',
//           severity: evt.confidenceScore > 90 ? 'high' : 'medium'
//         }));
//
//         // 시간순 정렬 및 상태 업데이트
//         mappedEvents.sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime());
//         setEvents(mappedEvents);
//       } catch (error) {
//         console.error('이벤트 데이터를 불러오지 못했습니다:', error);
//       }
//     };
//
//     // 처음 한 번 바로 불러오고, 10초마다 갱신
//     fetchEvents();
//     const interval = setInterval(fetchEvents, 10000);
//
//     return () => clearInterval(interval); // 컴포넌트가 꺼질 때 타이머 정리
//   }, [user]);
//
//   // 카테고리 필터링
//   useEffect(() => {
//     if (categoryFilter === 'all') {
//       setFilteredCameras(cameras);
//     } else if (categoryFilter === 'office') {
//       setFilteredCameras(cameras.filter(cam => cam.category === 'office' || cam.category === 'parking'));
//     } else {
//       setFilteredCameras(cameras.filter(cam => cam.category === categoryFilter));
//     }
//   }, [categoryFilter, cameras]);
//
//   useEffect(() => {
//     setCurrentPage(1);
//   }, [categoryFilter, layout]);
//
//   // UI 핸들러들
//   const handleCameraSelect = (cameraId: string) => {
//     const camera = cameras.find(c => c.id === cameraId);
//     if (camera) setSelectedCamera(camera);
//   };
//
//   const handleCameraFullscreen = (cameraId: string) => {
//     const camera = cameras.find(c => c.id === cameraId);
//     if (camera) setFullscreenCamera(camera);
//   };
//
//   const handleEventClick = (eventId: string) => {
//     const event = events.find(e => e.id === eventId);
//     if (event) setSelectedEvent(event);
//   };
//
//   // 💡 이벤트 상태 변경 시 백엔드에도 반영하도록 수정 필요 (현재는 로컬만 변경)
//   const handleEventStatusChange = async (status: AnomalyEvent['status']) => {
//     if (selectedEvent) {
//       try {
//         // 실제 운영 시에는 PATCH 요청으로 백엔드 상태 업데이트
//         // await apiClient.patch(`/v1/kindergartens/${KINDERGARTEN_ID}/events/${selectedEvent.id}`, { status });
//
//         setEvents(prev => prev.map(e =>
//           e.id === selectedEvent.id
//             ? {
//                 ...e,
//                 status,
//                 ...(status === 'resolved' ? { resolvedBy: user?.name, resolvedAt: new Date() } : {})
//               }
//             : e
//         ));
//         if (status === 'resolved') {
//           setSelectedEvent(null);
//         }
//       } catch (error) {
//         console.error('이벤트 상태 변경 실패:', error);
//       }
//     }
//   };
//
//   const handleEventAddNote = (note: string) => {
//     alert(`메모가 추가되었습니다:\n"${note}"\n\n실제 환경에서는 데이터베이스에 저장됩니다.`);
//   };
//
//   const handleQuickFullscreen = () => { if (filteredCameras.length > 0) setFullscreenCamera(filteredCameras[0]); };
//   const handleQuickPause = () => { setIsVideoPaused(!isVideoPaused); };
//   const handleQuickDownload = () => { alert('선택된 카메라의 영상을 다운로드합니다.'); };
//   const handleCategoryFilter = (category: typeof categoryFilter) => { setCategoryFilter(category); };
//
//   const cameraStats = {
//     total: cameras.length,
//     online: cameras.filter(c => c.status === 'online').length,
//     offline: cameras.filter(c => c.status === 'offline').length
//   };
//
//   const getCamerasPerPage = () => {
//     if (layout === '1x1') return 1;
//     if (layout === '2x2') return 4;
//     return 9; // 3x3
//   };
//
//   const camerasPerPage = getCamerasPerPage();
//   const totalPages = Math.ceil(filteredCameras.length / camerasPerPage);
//   const startIndex = (currentPage - 1) * camerasPerPage;
//   const displayedCameras = filteredCameras.slice(startIndex, startIndex + camerasPerPage);
//
//   if (isLoading || !user) {
//     return (
//       <div className="min-h-screen flex items-center justify-center bg-gray-50">
//         <div className="flex flex-col items-center gap-4">
//           <div className="w-8 h-8 border-4 border-purple-200 border-t-purple-600 rounded-full animate-spin"></div>
//           <p className="text-gray-500 font-medium">데이터를 불러오는 중입니다...</p>
//         </div>
//       </div>
//     );
//   }
//
//   const permissions = rolePermissions[user.role];
//
//   return (
//     <>
//       <div className="h-screen flex flex-col bg-gray-50">
//         <TopBar
//             currentRole={user.role}
//             username={user.name}
//             onRoleChange={switchRole}
//         />
//
//         <div className="flex-1 flex overflow-hidden">
//           <Sidebar
//               currentRole={user.role}
//               userName={user.name}
//               cameraStats={cameraStats}
//               onCategoryFilter={handleCategoryFilter}
//               currentCategory={categoryFilter}
//           />
//
//           <main className="flex-1 p-6 overflow-auto">
//             <div className="mb-4 flex items-center justify-between">
//               <div>
//                 <h2 className="text-lg font-bold text-gray-900">실시간 모니터링</h2>
//                 <p className="text-sm text-gray-500">
//                   전체 {filteredCameras.length}개 카메라 중 {startIndex + 1}-{Math.min(startIndex + camerasPerPage, filteredCameras.length)}번째 • {layout === '1x1' ? '1×1' : layout === '2x2' ? '2×2' : '3×3'} 레이아웃
//                   {isVideoPaused && ' • 일시정지됨'}
//                 </p>
//               </div>
//
//               {totalPages > 1 && (
//                 <div className="flex items-center gap-2">
//                   <Button variant="outline" size="sm" className="h-8 w-8 p-0" onClick={() => setCurrentPage(p => Math.max(1, p - 1))} disabled={currentPage === 1}>
//                     <ChevronLeft className="h-4 w-4" />
//                   </Button>
//                   <span className="text-sm font-medium text-gray-600 w-12 text-center">
//                     {currentPage} / {totalPages}
//                   </span>
//                   <Button variant="outline" size="sm" className="h-8 w-8 p-0" onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))} disabled={currentPage === totalPages}>
//                     <ChevronRight className="h-4 w-4" />
//                   </Button>
//                 </div>
//               )}
//             </div>
//
//             <div className="mb-6">
//               <CCTVGrid
//                   cameras={displayedCameras}
//                   onCameraSelect={handleCameraSelect}
//                   onCameraFullscreen={handleCameraFullscreen}
//                   layout={layout}
//               />
//             </div>
//
//             <div className="border-t border-gray-200 pt-8 mt-4">
//               {metricsError && (
//                 <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg text-red-600 text-sm">
//                   {metricsError}
//                 </div>
//               )}
//
//               <section className="mb-8">
//                 <h2 className="text-lg font-bold text-gray-900 mb-4">시스템 리소스 현황</h2>
//                 <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
//                   {metrics.slice(0, 6).map((metric) => (
//                     <MetricGauge key={metric.id} metric={metric} />
//                   ))}
//                 </div>
//               </section>
//
//               <section className="mb-8">
//                 <h2 className="text-lg font-bold text-gray-900 mb-4">상세 데이터 그리드</h2>
//                 <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
//                   <table className="w-full text-sm text-left">
//                     <thead className="bg-gray-50 border-b border-gray-200 text-gray-600 font-medium">
//                       <tr>
//                         <th className="px-6 py-3">ID</th>
//                         <th className="px-6 py-3">메트릭명</th>
//                         <th className="px-6 py-3">값</th>
//                         <th className="px-6 py-3">단위</th>
//                         <th className="px-6 py-3">생성일시</th>
//                       </tr>
//                     </thead>
//                     <tbody className="divide-y divide-gray-200">
//                       {metrics.map((metric) => (
//                         <tr key={metric.id} className="hover:bg-gray-50 transition-colors">
//                           <td className="px-6 py-4 text-gray-500">{metric.id}</td>
//                           <td className="px-6 py-4 font-medium text-gray-900">{metric.metricName}</td>
//                           <td className="px-6 py-4 font-semibold text-purple-600">{metric.value}</td>
//                           <td className="px-6 py-4 text-gray-500">{metric.unit}</td>
//                           <td className="px-6 py-4 text-gray-400">
//                             {new Date(metric.createdAt).toLocaleString('ko-KR')}
//                           </td>
//                         </tr>
//                       ))}
//                     </tbody>
//                   </table>
//                 </div>
//               </section>
//             </div>
//           </main>
//
//           <RightPanel
//               events={events}
//               onEventClick={handleEventClick}
//               currentRole={user.role}
//               onLayoutChange={setLayout}
//               currentLayout={layout}
//               isRecording={isRecording}
//               onRecordingToggle={() => setIsRecording(!isRecording)}
//               onQuickFullscreen={handleQuickFullscreen}
//               onQuickPause={handleQuickPause}
//               onQuickDownload={handleQuickDownload}
//               isVideoPaused={isVideoPaused}
//           />
//         </div>
//       </div>
//
//       {selectedCamera && (
//           <CameraDetailModal
//               camera={selectedCamera}
//               onClose={() => setSelectedCamera(null)}
//               onFullscreen={() => {
//                 setFullscreenCamera(selectedCamera);
//                 setSelectedCamera(null);
//               }}
//           />
//       )}
//
//       {selectedEvent && (
//           <EventDetailModal
//               event={selectedEvent}
//               onClose={() => setSelectedEvent(null)}
//               onStatusChange={handleEventStatusChange}
//               onAddNote={handleEventAddNote}
//               canResolve={permissions.canResolveAnomaly}
//           />
//       )}
//
//       {fullscreenCamera && (
//           <FullscreenView
//               camera={fullscreenCamera}
//               onClose={() => setFullscreenCamera(null)}
//           />
//       )}
//     </>
//   );
// }