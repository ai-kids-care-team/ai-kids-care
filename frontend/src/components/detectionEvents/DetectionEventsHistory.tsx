'use client';

import { useState } from 'react';
import { Search, Calendar, Filter, ChevronLeft, ChevronRight, AlertCircle } from 'lucide-react';

import { useAppSelector, useAppDispatch } from '@/store/hook';
import { switchRole } from '@/store/slices/userSlice';
//import { useGetEventLogsQuery, useUpdateEventStatusMutation } from '@/entities/detectionEvents';
import { useGetEventsQuery, useUpdateEventStatusMutation } from '@/services/apis/event.api';
import { DetectionEventsDetailModal } from '@/components/detectionEvents/DetectionEventsDetailModal';
import { TopBar } from '@/layout/TopBar';
import { Sidebar } from '@/layout/Sidebar';
import { Button } from '@/components/shared/ui/button';
import type { AnomalyEvent, AnomalyType } from '@/types/anomaly';
import { rolePermissions } from '@/types/user-role';

const KINDERGARTEN_ID = '1';

export function DetectionEventsHistory() {
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.user);

  // 모달 상태
  const [selectedEvent, setSelectedEvent] = useState<AnomalyEvent | null>(null);

  // 필터 및 페이징 상태
  const [currentPage, setCurrentPage] = useState(1);
  const [filterType, setFilterType] = useState<AnomalyType | 'ALL'>('ALL');
  const [filterStatus, setFilterStatus] = useState<'ALL' | 'active' | 'reviewing' | 'resolved'>('ALL');
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');

  // RTK Query를 이용한 데이터 패칭 (파라미터가 바뀌면 자동으로 재요청)
  //const { data, isFetching } = useGetEventLogsQuery({
  const { data, isFetching } = useGetEventsQuery({
    kindergartenId: KINDERGARTEN_ID,
    page: currentPage,
    size: 10,
    type: filterType !== 'ALL' ? filterType : undefined,
    status: filterStatus !== 'ALL' ? filterStatus : undefined,
    startDate: startDate || undefined,
    endDate: endDate || undefined,
  }, {
    skip: !user, // 유저 정보가 없으면 요청 안함
  });

  const [updateStatusApi] = useUpdateEventStatusMutation();

  const events: AnomalyEvent[] = data?.data || [];
  const totalEvents = data?.totalElements || 0;
  const totalPages = data?.totalPages || 1;

  if (!user) return null;
  const currentRole = user.role;
  const permissions = rolePermissions[currentRole];

  const handleEventClick = (event: AnomalyEvent) => {
    setSelectedEvent(event);
  };

  const handleEventStatusChange = async (status: AnomalyEvent['status']) => {
    if (selectedEvent) {
      try {
        //await updateStatusApi({ eventId: selectedEvent.id, status }).unwrap();
        await updateStatusApi({ kindergartenId: KINDERGARTEN_ID, eventId: selectedEvent.id, status }).unwrap();
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

  return (
    <>
      <div className="h-screen flex flex-col bg-gray-50">
        <TopBar currentRole={currentRole} username={user.name} onRoleChange={(r) => dispatch(switchRole(r))} />

        <div className="flex-1 flex overflow-hidden">
          <Sidebar
            currentRole={currentRole}
            userName={user.name}
            cameraStats={{ total: 0, online: 0, offline: 0 }}
            onCategoryFilter={() => {}}
            currentCategory={'all'}
          />

          <main className="flex-1 p-6 overflow-auto bg-slate-50">
            <div className="max-w-7xl mx-auto">
              <div className="mb-6">
                <h1 className="text-2xl font-bold text-slate-900">과거 위험 감지 기록</h1>
                <p className="text-sm text-slate-500 mt-1">AI가 감지한 전체 과거 내역을 검색하고 분석합니다.</p>
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
                    <Button variant="outline" onClick={resetFilters} className="h-[38px] px-3 mt-auto">초기화</Button>
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
                        <tr><td colSpan={6} className="px-6 py-12 text-center text-slate-500">데이터를 불러오는 중입니다...</td></tr>
                      ) : events.length === 0 ? (
                        <tr><td colSpan={6} className="px-6 py-12 text-center text-slate-500">조건에 일치하는 기록이 없습니다.</td></tr>
                      ) : (
                        events.map((event) => (
                          <tr key={event.id} className="hover:bg-slate-50 transition-colors">
                            <td className="px-6 py-4 text-slate-900">
                              {new Date(event.timestamp).toLocaleString('ko-KR')}
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
                              <Button variant="outline" size="sm" onClick={() => handleEventClick(event)} className="text-xs h-8">
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
                      <Button variant="outline" size="sm" className="h-8 w-8 p-0" onClick={() => setCurrentPage(p => Math.max(1, p - 1))} disabled={currentPage === 1}>
                        <ChevronLeft className="h-4 w-4" />
                      </Button>
                      <span className="text-sm font-medium text-slate-600 w-12 text-center">{currentPage} / {totalPages}</span>
                      <Button variant="outline" size="sm" className="h-8 w-8 p-0" onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))} disabled={currentPage === totalPages}>
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

      {selectedEvent && (
        <DetectionEventsDetailModal
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