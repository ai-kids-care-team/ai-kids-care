'use client';
export const dynamic = 'force-dynamic';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/app/context/AuthContext';
import { getDashboardMetrics, DashboardMetric } from '@/lib/api';
import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts';
import { ChevronLeft, ChevronRight } from 'lucide-react'; // 페이지네이션용 아이콘 추가

import { TopBar } from '../components/TopBar';
import { Sidebar } from '../components/Sidebar';
import { CCTVGrid } from '../components/CCTVGrid';
import { RightPanel } from '../components/RightPanel';
import { CameraDetailModal } from '../components/CameraDetailModal';
import { EventDetailModal } from '../components/EventDetailModal';
import { FullscreenView } from '../components/FullscreenView';
//import { Button } from './ui/button'; // 페이지네이션 버튼용
import { Button } from '../components/ui/button';
import type { Camera, AnomalyEvent, AnomalyType } from '../types/anomaly';
import { rolePermissions } from '../types/anomaly';

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
            <Pie
              data={data}
              cx="50%"
              cy="70%"
              startAngle={180}
              endAngle={0}
              innerRadius="60%"
              outerRadius="100%"
              dataKey="value"
              stroke="none"
            >
              {data.map((entry, index) => (
                <Cell key={index} fill={entry.color} />
              ))}
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
    {
      id: 'EVT-001',
      timestamp: new Date(Date.now() - 2 * 60000),
      cameraId: 'CAM-001',
      cameraName: '정문 입구',
      type: 'Trespass',
      confidence: 95,
      location: '1층 현관',
      status: 'active',
      severity: 'high'
    },
    {
      id: 'EVT-002',
      timestamp: new Date(Date.now() - 15 * 60000),
      cameraId: 'CAM-009',
      cameraName: '운동장',
      type: 'Fight',
      confidence: 92,
      location: '야외 운동공간',
      status: 'reviewing',
      severity: 'high'
    },
    {
      id: 'EVT-003',
      timestamp: new Date(Date.now() - 45 * 60000),
      cameraId: 'CAM-002',
      cameraName: '놀이터',
      type: 'Wander',
      confidence: 78,
      location: '야외 놀이공간',
      status: 'resolved',
      severity: 'low'
    }
  ];

  return events.sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime());
};

export default function DashboardPage() {
  const router = useRouter();
  const { user, token, switchRole, isAuthenticated, isLoading } = useAuth();

  const [cameras, setCameras] = useState<Camera[]>(allCameras);
  const [filteredCameras, setFilteredCameras] = useState<Camera[]>(allCameras);
  const [events, setEvents] = useState<AnomalyEvent[]>(generateMockEvents());
  const [layout, setLayout] = useState<'1x1' | '2x2' | '3x3'>('2x2');
  const [isRecording, setIsRecording] = useState(true);
  const [selectedCamera, setSelectedCamera] = useState<Camera | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<AnomalyEvent | null>(null);
  const [fullscreenCamera, setFullscreenCamera] = useState<Camera | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<'all' | Camera['category'] | 'parking'>('all');
  const [isVideoPaused, setIsVideoPaused] = useState(false);

  // 페이지네이션 상태 추가
  const [currentPage, setCurrentPage] = useState(1);

  const [metrics, setMetrics] = useState<DashboardMetric[]>([]);
  const [metricsError, setMetricsError] = useState('');

  useEffect(() => {
    // 데모 환경이 연결되지 않았을 때 오류를 막으려면 임시로 주석 처리 가능
    if (!isAuthenticated && !isLoading) {
      router.push('/login');
    }
  }, [isAuthenticated, isLoading, router]);

  useEffect(() => {
    if (!token) return;

    getDashboardMetrics(token)
      .then(setMetrics)
      .catch(() => setMetricsError('메트릭 데이터를 불러오는데 실패했습니다.'));
  }, [token]);

  useEffect(() => {
    if (!user) return;

    const permissions = rolePermissions[user.role];

    let availableCameras = allCameras;

    if (permissions.canViewAllCameras) {
      availableCameras = allCameras;
    } else if (permissions.canViewOwnClassroom) {
      if (user.role === 'teacher') {
        availableCameras = allCameras.filter(cam =>
            cam.category === 'classroom' && cam.assignedTeacher === 'teacher1'
        );
      } else if (user.role === 'guardian') {
        availableCameras = allCameras.filter(cam =>
            cam.category === 'classroom' || cam.category === 'playground'
        );
      }
    }

    setCameras(availableCameras);
    setFilteredCameras(availableCameras);
  }, [user]);

  // 카테고리 필터링 개선 (office 선택 시 parking 포함)
  useEffect(() => {
    if (categoryFilter === 'all') {
      setFilteredCameras(cameras);
    } else if (categoryFilter === 'office') {
      setFilteredCameras(cameras.filter(cam => cam.category === 'office' || cam.category === 'parking'));
    } else {
      setFilteredCameras(cameras.filter(cam => cam.category === categoryFilter));
    }
  }, [categoryFilter, cameras]);

  // 카테고리나 레이아웃이 바뀌면 첫 페이지로 초기화
  useEffect(() => {
    setCurrentPage(1);
  }, [categoryFilter, layout]);

  useEffect(() => {
    const interval = setInterval(() => {
      if (Math.random() < 0.1) {
        const randomCamera = allCameras[Math.floor(Math.random() * allCameras.length)];
        const randomType = anomalyTypes[Math.floor(Math.random() * anomalyTypes.length)];

        const newEvent: AnomalyEvent = {
          id: `EVT-${Date.now()}`,
          timestamp: new Date(),
          cameraId: randomCamera.id,
          cameraName: randomCamera.name,
          type: randomType,
          confidence: Math.floor(Math.random() * 30) + 70,
          location: randomCamera.location,
          status: 'active',
          severity: Math.random() > 0.7 ? 'high' : Math.random() > 0.4 ? 'medium' : 'low'
        };

        setEvents(prev => [newEvent, ...prev].slice(0, 10));
      }
    }, 30000);

    return () => clearInterval(interval);
  }, []);

  const handleCameraSelect = (cameraId: string) => {
    const camera = cameras.find(c => c.id === cameraId);
    if (camera) {
      setSelectedCamera(camera);
    }
  };

  const handleCameraFullscreen = (cameraId: string) => {
    const camera = cameras.find(c => c.id === cameraId);
    if (camera) {
      setFullscreenCamera(camera);
    }
  };

  const handleEventClick = (eventId: string) => {
    const event = events.find(e => e.id === eventId);
    if (event) {
      setSelectedEvent(event);
    }
  };

  const handleEventStatusChange = (status: AnomalyEvent['status']) => {
    if (selectedEvent) {
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
  };

  const handleEventAddNote = (note: string) => {
    console.log('메모 추가:', note, '이벤트 ID:', selectedEvent?.id);
    alert(`메모가 추가되었습니다:\n"${note}"\n\n실제 환경에서는 데이터베이스에 저장됩니다.`);
  };

  const handleQuickFullscreen = () => {
    if (filteredCameras.length > 0) {
      setFullscreenCamera(filteredCameras[0]);
    }
  };

  const handleQuickPause = () => {
    setIsVideoPaused(!isVideoPaused);
  };

  const handleQuickDownload = () => {
    alert('영상 다운로드 기능이 실행됩니다.\n실제 환경에서는 선택된 카메라의 영상을 다운로드합니다.');
  };

  const handleCategoryFilter = (category: typeof categoryFilter) => {
    setCategoryFilter(category);
  };

  const cameraStats = {
    total: cameras.length,
    online: cameras.filter(c => c.status === 'online').length,
    offline: cameras.filter(c => c.status === 'offline').length
  };

  // 페이지네이션 계산 로직
  const getCamerasPerPage = () => {
    if (layout === '1x1') return 1;
    if (layout === '2x2') return 4;
    return 9; // 3x3
  };

  const camerasPerPage = getCamerasPerPage();
  const totalPages = Math.ceil(filteredCameras.length / camerasPerPage);
  const startIndex = (currentPage - 1) * camerasPerPage;

  // 현재 페이지에 보여줄 카메라 배열만 잘라내기
  const displayedCameras = filteredCameras.slice(startIndex, startIndex + camerasPerPage);

  if (isLoading || !user) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <p className="text-gray-500 font-medium">로딩 중...</p>
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

                {/* 상단 간이 페이지네이션 (선택사항) */}
                {totalPages > 1 && (
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-8 w-8 p-0"
                      onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                      disabled={currentPage === 1}
                    >
                      <ChevronLeft className="h-4 w-4" />
                    </Button>
                    <span className="text-sm font-medium text-gray-600 w-12 text-center">
                      {currentPage} / {totalPages}
                    </span>
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-8 w-8 p-0"
                      onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                      disabled={currentPage === totalPages}
                    >
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                )}
              </div>

              <div className="mb-6">
                <CCTVGrid
                    cameras={displayedCameras} // 전체 대신 잘라낸 배열 전달
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
                    {metrics.slice(0, 6).map((metric) => (
                      <MetricGauge key={metric.id} metric={metric} />
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