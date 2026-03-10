'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/app/context/AuthContext';
import { apiClient } from '@/lib/apiClient';
import { Search, Calendar, Filter, ChevronLeft, ChevronRight, AlertCircle } from 'lucide-react';

import { TopBar } from '../components/TopBar';
import { Sidebar } from '../components/Sidebar';
import { EventDetailModal } from '../components/EventDetailModal';
import { Button } from '../components/ui/button';
import type { AnomalyEvent, AnomalyType } from '../types/anomaly';
import { rolePermissions } from '../types/anomaly';

// 임시 유치원 ID (전역 상태나 토큰에서 가져오는 것으로 대체 가능)
const KINDERGARTEN_ID = '1';

// API 응답 페이징 타입 (api.ts에 정의한 PaginatedResponse 사용 가능)
interface PaginatedEvents {
  data: AnomalyEvent[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
}

export default function EventsPage() {
  const router = useRouter();
  const { user, token, switchRole, isAuthenticated, isLoading } = useAuth();

  // 데이터 상태
  const [events, setEvents] = useState<AnomalyEvent[]>([]);
  const [totalEvents, setTotalEvents] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [isFetching, setIsFetching] = useState(true);

  // 모달 상태
  const [selectedEvent, setSelectedEvent] = useState<AnomalyEvent | null>(null);

  // 필터 및 페이징 상태
  const [currentPage, setCurrentPage] = useState(1);
  const [filterType, setFilterType] = useState<AnomalyType | 'ALL'>('ALL');
  const [filterStatus, setFilterStatus] = useState<'ALL' | 'active' | 'reviewing' | 'resolved'>('ALL');
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');

  // 1. 인증 체크
  useEffect(() => {
    if (!isAuthenticated && !isLoading) {
      router.push('/login');
    }
  }, [isAuthenticated, isLoading, router]);

  // 2. 과거 이벤트 목록 불러오기 (API 연동)
  useEffect(() => {
    if (!user) return;

    const fetchEventsLog = async () => {
      setIsFetching(true);
      try {
        // 백엔드 API 명세에 맞게 쿼리 파라미터 구성
        const queryParams = new URLSearchParams({
          page: String(currentPage),
          size: '10',
          ...(filterType !== 'ALL' && { type: filterType }),
          ...(filterStatus !== 'ALL' && { status: filterStatus }),
          ...(startDate && { startDate }),
          ...(endDate && { endDate }),
        });

        // 실제 API 호출: /v1/kindergartens/{id}/events?page=1&size=10...
        const response = await apiClient.get(`/v1/kindergartens/${KINDERGARTEN_ID}/events?${queryParams}`);

        // 백엔드 응답 구조(PaginatedResponse)에 맞게 매핑
        const fetchedData = response.data.data || response.data;

        const mappedEvents: AnomalyEvent[] = fetchedData.map((evt: any) => ({
          id: evt.id,
          timestamp: new Date(evt.detectedAt || evt.createdAt),
          cameraId: evt.cameraId,
          cameraName: evt.cameraName || `카메라 ${evt.cameraId}`,
          type: evt.eventType || 'Wander',
          confidence: evt.confidenceScore || 0,
          location: evt.location || '위치 미상',
          status: evt.isReviewed ? 'resolved' : (evt.status || 'active'),
          severity: evt.confidenceScore > 90 ? 'high' : evt.confidenceScore > 70 ? 'medium' : 'low',
        }));

        setEvents(mappedEvents);

        // 페이징 메타데이터 업데이트 (백엔드에서 내려주는 정보 활용)
        setTotalPages(response.data.totalPages || 1);
        setTotalEvents(response.data.totalElements || mappedEvents.length);

      } catch (error) {
        console.error('과거 이벤트 기록을 불러오지 못했습니다:', error);
      } finally {
        setIsFetching(false);
      }
    };

    fetchEventsLog();
  }, [user, currentPage, filterType, filterStatus, startDate, endDate]);

  // UI 핸들러
  const handleEventClick = (event: AnomalyEvent) => {
    setSelectedEvent(event);
  };

  const handleEventStatusChange = async (status: AnomalyEvent['status']) => {
    if (selectedEvent) {
      try {
        // 실제 운영 시에는 PATCH 요청으로 백엔드 상태 업데이트
        // await apiClient.patch(`/v1/kindergartens/${KINDERGARTEN_ID}/events/${selectedEvent.id}`, { status });

        setEvents(prev => prev.map(e =>
          e.id === selectedEvent.id ? { ...e, status } : e
        ));

        if (status === 'resolved') {
          setSelectedEvent(null);
        }
      } catch (error) {
        console.error('이벤트 상태 변경 실패:', error);
      }
    }
  };

  const resetFilters = () => {
    setFilterType('ALL');
    setFilterStatus('ALL');
    setStartDate('');
    setEndDate('');
    setCurrentPage(1);
  };

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
        <TopBar currentRole={user.role} username={user.name} onRoleChange={switchRole} />

        <div className="flex-1 flex overflow-hidden">
          {/* 사이드바는 대시보드와 동일하게 유지하되, 카테고리 필터 프롭스는 임시 함수로 처리 */}
          <Sidebar
            currentRole={user.role}
            userName={user.name}
            cameraStats={{ total: 0, online: 0, offline: 0 }} // 과거 기록 페이지에서는 생략 또는 0 처리
            onCategoryFilter={() => {}}
            currentCategory={'all'}
          />

          <main className="flex-1 p-6 overflow-auto bg-slate-50">
            <div className="max-w-7xl mx-auto">
              {/* 헤더 영역 */}
              <div className="mb-6">
                <h1 className="text-2xl font-bold text-slate-900">과거 위험 감지 기록</h1>
                <p className="text-sm text-slate-500 mt-1">
                  AI가 감지한 전체 과거 내역을 검색하고 분석합니다.
                </p>
              </div>

              {/* 필터 영역 */}
              <div className="bg-white p-4 rounded-xl shadow-sm border border-slate-200 mb-6">
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4 items-end">
                  <div>
                    <label className="block text-xs font-medium text-slate-500 mb-1">시작일</label>
                    <div className="relative">
                      <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                      <input
                        type="date"
                        value={startDate}
                        onChange={(e) => { setStartDate(e.target.value); setCurrentPage(1); }}
                        className="w-full pl-9 pr-3 py-2 text-sm border border-slate-300 rounded-lg focus:ring-2 focus:ring-purple-500 outline-none"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-500 mb-1">종료일</label>
                    <div className="relative">
                      <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                      <input
                        type="date"
                        value={endDate}
                        onChange={(e) => { setEndDate(e.target.value); setCurrentPage(1); }}
                        className="w-full pl-9 pr-3 py-2 text-sm border border-slate-300 rounded-lg focus:ring-2 focus:ring-purple-500 outline-none"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-500 mb-1">이벤트 유형</label>
                    <div className="relative">
                      <Filter className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                      <select
                        value={filterType}
                        onChange={(e) => { setFilterType(e.target.value as any); setCurrentPage(1); }}
                        className="w-full pl-9 pr-3 py-2 text-sm border border-slate-300 rounded-lg focus:ring-2 focus:ring-purple-500 outline-none appearance-none"
                      >
                        <option value="ALL">모든 유형</option>
                        <option value="Trespass">무단 침입 (Trespass)</option>
                        <option value="Fight">폭력/싸움 (Fight)</option>
                        <option value="Swoon">쓰러짐 (Swoon)</option>
                        <option value="Wander">배회 (Wander)</option>
                      </select>
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <div className="flex-1">
                      <label className="block text-xs font-medium text-slate-500 mb-1">처리 상태</label>
                      <select
                        value={filterStatus}
                        onChange={(e) => { setFilterStatus(e.target.value as any); setCurrentPage(1); }}
                        className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:ring-2 focus:ring-purple-500 outline-none"
                      >
                        <option value="ALL">전체 상태</option>
                        <option value="active">미확인 (Active)</option>
                        <option value="reviewing">확인 중 (Reviewing)</option>
                        <option value="resolved">처리 완료 (Resolved)</option>
                      </select>
                    </div>
                    <Button variant="outline" onClick={resetFilters} className="h-[38px] px-3 mt-auto">
                      초기화
                    </Button>
                  </div>
                </div>
              </div>

              {/* 데이터 테이블 영역 */}
              <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm text-left">
                    <thead className="bg-slate-50 border-b border-slate-200 text-slate-600 font-medium">
                      <tr>
                        <th className="px-6 py-4">발생 일시</th>
                        <th className="px-6 py-4">카메라 위치</th>
                        <th className="px-6 py-4">이벤트 유형</th>
                        <th className="px-6 py-4">AI 확신도</th>
                        <th className="px-6 py-4">상태</th>
                        <th className="px-6 py-4">액션</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {isFetching ? (
                        <tr>
                          <td colSpan={6} className="px-6 py-12 text-center text-slate-500">
                            데이터를 불러오는 중입니다...
                          </td>
                        </tr>
                      ) : events.length === 0 ? (
                        <tr>
                          <td colSpan={6} className="px-6 py-12 text-center text-slate-500">
                            조건에 일치하는 기록이 없습니다.
                          </td>
                        </tr>
                      ) : (
                        events.map((event) => (
                          <tr key={event.id} className="hover:bg-slate-50 transition-colors">
                            <td className="px-6 py-4 text-slate-900">
                              {event.timestamp.toLocaleString('ko-KR', {
                                year: 'numeric', month: '2-digit', day: '2-digit',
                                hour: '2-digit', minute: '2-digit', second: '2-digit'
                              })}
                            </td>
                            <td className="px-6 py-4 font-medium text-slate-700">{event.cameraName}</td>
                            <td className="px-6 py-4">
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-purple-50 text-purple-700 font-medium text-xs border border-purple-100">
                                <AlertCircle className="w-3.5 h-3.5" />
                                {event.type}
                              </span>
                            </td>
                            <td className="px-6 py-4">
                              <div className="flex items-center gap-2">
                                <div className="w-16 h-2 bg-slate-100 rounded-full overflow-hidden">
                                  <div
                                    className={`h-full rounded-full ${event.confidence > 90 ? 'bg-red-500' : event.confidence > 70 ? 'bg-amber-500' : 'bg-emerald-500'}`}
                                    style={{ width: `${event.confidence}%` }}
                                  />
                                </div>
                                <span className="text-xs text-slate-500 font-medium">{event.confidence}%</span>
                              </div>
                            </td>
                            <td className="px-6 py-4">
                              <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${
                                event.status === 'active' ? 'bg-red-100 text-red-700' :
                                event.status === 'reviewing' ? 'bg-amber-100 text-amber-700' :
                                'bg-emerald-100 text-emerald-700'
                              }`}>
                                {event.status === 'active' ? '미확인' : event.status === 'reviewing' ? '확인 중' : '처리 완료'}
                              </span>
                            </td>
                            <td className="px-6 py-4">
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => handleEventClick(event)}
                                className="text-xs h-8"
                              >
                                상세 보기
                              </Button>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>

                {/* 페이징 컨트롤 */}
                {!isFetching && events.length > 0 && (
                  <div className="flex items-center justify-between px-6 py-4 border-t border-slate-200 bg-slate-50">
                    <p className="text-sm text-slate-500">
                      총 <span className="font-medium text-slate-900">{totalEvents}</span>건 중 {(currentPage - 1) * 10 + 1}-{Math.min(currentPage * 10, totalEvents)}
                    </p>
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
                      <span className="text-sm font-medium text-slate-600 w-12 text-center">
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
                  </div>
                )}
              </div>
            </div>
          </main>
        </div>
      </div>

      {/* 이벤트 상세 보기 모달 */}
      {selectedEvent && (
        <EventDetailModal
          event={selectedEvent}
          onClose={() => setSelectedEvent(null)}
          onStatusChange={handleEventStatusChange}
          onAddNote={(note) => { alert(`메모 추가됨: ${note}`); }}
          canResolve={permissions.canResolveAnomaly}
        />
      )}
    </>
  );
}