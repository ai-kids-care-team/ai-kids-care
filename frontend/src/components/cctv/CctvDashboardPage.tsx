'use client';

import { useMemo, useState } from 'react';
import { Bell } from 'lucide-react';

import { useAppSelector } from '@/store/hook';
import type { UserRole } from '@/types/user-role';
import { Button } from '@/components/shared/ui/button';

import { useKindergartenName } from '@/components/cctv/hooks/useKindergartenName';
import { useCctvCameras } from '@/components/cctv/hooks/useCctvCameras';
import { useCctvAlerts } from '@/components/cctv/hooks/useCctvAlerts';
import { useCctvGridData, type LayoutMode } from '@/components/cctv/hooks/useCctvGridData';
import { useQuickPlaylist } from '@/components/cctv/hooks/useQuickPlaylist';
import type { CctvCategoryKey } from '@/lib/cctvFormat';
import { CctvSidebar } from '@/components/cctv/CctvSidebar';
import { CctvControlPanel } from '@/components/cctv/CctvControlPanel';
import { CctvCameraGrid } from '@/components/cctv/CctvCameraGrid';
import { CctvAlertPanel } from '@/components/cctv/CctvAlertPanel';
import { CctvFullscreenPlayer } from '@/components/cctv/CctvFullscreenPlayer';
import { CctvCameraDetailModal } from '@/components/cctv/CctvCameraDetailModal';
import type { CctvCameraVO } from '@/types/cctv.vo';

/**
 * CCTV 대시보드 편성 컨테이너(cctv-dashboard-refactor-alerts / C5, QLT-01+ARC-04).
 * 상태/파생 로직은 `components/cctv/hooks/*`에, 화면 조각은 `components/cctv/Cctv*`에
 * 위임하고, 이 컴포넌트는 레이아웃 편성 + 컴포넌트 간 이벤트 라우팅만 담당한다.
 */
export function CctvDashboardPage() {
  const sessionUser = useAppSelector((s) => s.user.user);
  const isAuthenticated = useAppSelector((s) => s.user.isAuthenticated);

  const [layout, setLayout] = useState<LayoutMode>('2x2');
  const [categoryFilter, setCategoryFilter] = useState<CctvCategoryKey>('all');
  const [currentPage, setCurrentPage] = useState(0);
  const [focusedCameraId, setFocusedCameraId] = useState<number | null>(null);
  const [isVideoPaused, setIsVideoPaused] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const [selectedCamera, setSelectedCamera] = useState<CctvCameraVO | null>(null);

  const effectiveRole: UserRole = sessionUser?.role ?? 'GUARDIAN';
  const loginIdDisplay =
    sessionUser?.loginId?.trim() || sessionUser?.username?.trim() || (isAuthenticated ? '—' : '게스트');
  const personNameDisplay = sessionUser?.name?.trim() || '—';

  const sessionKindergartenId = useMemo(() => {
    const direct = Number((sessionUser as { kindergartenId?: number } | null)?.kindergartenId ?? 0);
    return Number.isFinite(direct) && direct > 0 ? Math.trunc(direct) : null;
  }, [sessionUser]);

  const shouldScopeToOwnKindergarten = effectiveRole === 'KINDERGARTEN_ADMIN' || effectiveRole === 'TEACHER';
  // CCTV / live streams are restricted to KINDERGARTEN_ADMIN: the backend denies
  // camera/stream/detection reads to TEACHER (and platform roles), so teachers get the
  // restricted view here instead of issuing now-forbidden requests.
  const canViewLiveStreams = effectiveRole === 'KINDERGARTEN_ADMIN';

  const kindergartenName = useKindergartenName(sessionKindergartenId);
  const kindergartenAffiliationLabel = useMemo(() => {
    if (sessionKindergartenId != null && sessionKindergartenId > 0) {
      return kindergartenName ?? '불러오는 중…';
    }
    if (effectiveRole === 'SUPERADMIN' || effectiveRole === 'PLATFORM_IT_ADMIN') {
      return '전체(유치원 미지정)';
    }
    return '소속 유치원 없음';
  }, [effectiveRole, kindergartenName, sessionKindergartenId]);

  const { cameras, loading, streamMapByCamera } = useCctvCameras(sessionKindergartenId, canViewLiveStreams);

  // UX-02: 실시간 이상 알림 — REST 초기 이력 + SSE 증분(테넌트는 백엔드 세션이 강제하므로
  // reconnectKey는 재연결 트리거일 뿐, 요청에 kindergartenId를 싣지 않는다).
  const { events, highlightedEventIds } = useCctvAlerts(canViewLiveStreams, sessionKindergartenId);

  const {
    filteredCameras,
    scopedEvents,
    itemsPerPage,
    totalPages,
    safePage,
    displayGridSlots,
    eventsByCamera,
    activeAlertCount,
    cameraStats,
    categoryCounts,
    gridCols,
  } = useCctvGridData({
    cameras,
    events,
    sessionKindergartenId,
    shouldScopeToOwnKindergarten,
    layout,
    categoryFilter,
    currentPage,
    focusedCameraId,
  });

  const {
    quickPlaylistIndex,
    playlistFullscreenElRef,
    playlistCamera,
    playlistEmbedUrl,
    playlistSubLine,
    openPlaylistAt,
    goQuickPlaylistStep,
  } = useQuickPlaylist(filteredCameras, itemsPerPage, setCurrentPage, canViewLiveStreams);

  const selectedCameraKindergartenName = useKindergartenName(selectedCamera?.kindergartenId);

  return (
    <div className="flex h-full min-h-0 flex-col bg-gray-50">
      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-0 flex-1 overflow-hidden">
          <CctvSidebar
            role={effectiveRole}
            loginIdDisplay={loginIdDisplay}
            personNameDisplay={personNameDisplay}
            kindergartenAffiliationLabel={kindergartenAffiliationLabel}
            cameraStats={cameraStats}
            categoryCounts={categoryCounts}
            categoryFilter={categoryFilter}
            onSelectCategory={(key) => {
              setCategoryFilter(key);
              setCurrentPage(0);
              setFocusedCameraId(null);
            }}
            filteredCameras={filteredCameras}
            focusedCameraId={focusedCameraId}
            onToggleFocusCamera={(cameraId) => {
              setFocusedCameraId((prev) => (prev === cameraId ? null : cameraId));
              setCurrentPage(0);
            }}
          />

          {/* Main — 피그마 DashboardPage + CCTVGrid */}
          <main className="min-h-0 flex-1 overflow-auto p-6 flex flex-col">
            <div className="mb-4 flex shrink-0 flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-gray-900">실시간 모니터링</h2>
                <p className="text-sm text-gray-500">
                  {filteredCameras.length}개 카메라 등록 •{' '}
                  {layout === '1x1' ? '1×1' : layout === '2x2' ? '2×2' : '3×3'} 레이아웃
                  {isVideoPaused ? ' • 일시정지됨' : ''}
                </p>
              </div>
              {/* 이상 알림 버튼은 일단 숨김 (display: none) */}
              <div className="hidden">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="relative shrink-0 gap-1.5"
                  onClick={() => setShowNotifications((v) => !v)}
                >
                  <Bell className="h-4 w-4" />
                  이상 알림
                  {activeAlertCount > 0 && (
                    <span className="ml-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-semibold text-white">
                      {activeAlertCount > 99 ? '99+' : activeAlertCount}
                    </span>
                  )}
                </Button>
              </div>
            </div>

            {loading && (
              <div className="flex min-h-[320px] flex-1 items-center justify-center text-sm text-gray-400">
                불러오는 중…
              </div>
            )}

            {/* apiError 카드 제거: 이벤트/일부 데이터 조회 실패가 있어도 그리드는 계속 렌더됩니다. */}

            {!canViewLiveStreams && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                현재 역할에는 live CCTV 접근 권한이 없습니다. 향후 권한이 검증된 tenant context를
                통해서만 제공됩니다.
              </div>
            )}

            {!loading && (
              <CctvCameraGrid
                categoryFilter={categoryFilter}
                filteredCameras={filteredCameras}
                displayGridSlots={displayGridSlots}
                eventsByCamera={eventsByCamera}
                safePage={safePage}
                itemsPerPage={itemsPerPage}
                totalPages={totalPages}
                gridCols={gridCols}
                canViewLiveStreams={canViewLiveStreams}
                isVideoPaused={isVideoPaused}
                onOpenDetail={(camera) => setSelectedCamera(camera)}
                onOpenFullscreenAt={openPlaylistAt}
                onChangePage={setCurrentPage}
              />
            )}
          </main>

          <CctvControlPanel
            layout={layout}
            onChangeLayout={(next) => {
              setLayout(next);
              setCurrentPage(0);
            }}
            hasFilteredCameras={filteredCameras.length > 0}
            onOpenFullscreenPlaylist={() => {
              if (filteredCameras.length === 0) return;
              openPlaylistAt(0);
            }}
            isVideoPaused={isVideoPaused}
            onTogglePause={() => setIsVideoPaused((v) => !v)}
          />
        </div>

        {/* NotificationList — 피그마 하단 패널 */}
        {showNotifications && (
          <CctvAlertPanel
            events={scopedEvents}
            cameras={cameras}
            highlightedEventIds={highlightedEventIds}
            onClose={() => setShowNotifications(false)}
          />
        )}
      </div>
      {quickPlaylistIndex !== null && playlistCamera && (
        <CctvFullscreenPlayer
          containerRef={playlistFullscreenElRef}
          camera={playlistCamera}
          subLine={playlistSubLine}
          embedUrl={playlistEmbedUrl}
          isVideoPaused={isVideoPaused}
          quickPlaylistIndex={quickPlaylistIndex}
          totalCount={filteredCameras.length}
          onClose={() => {
            if (typeof document !== 'undefined') void document.exitFullscreen();
          }}
          onStep={goQuickPlaylistStep}
        />
      )}
      {selectedCamera && (
        <CctvCameraDetailModal
          camera={selectedCamera}
          kindergartenName={selectedCameraKindergartenName}
          canViewLiveStreams={canViewLiveStreams}
          streams={streamMapByCamera.get(selectedCamera.cameraId) ?? []}
          onClose={() => setSelectedCamera(null)}
        />
      )}
    </div>
  );
}
