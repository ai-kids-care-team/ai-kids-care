'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Bell,
  Camera,
  ChevronLeft,
  ChevronRight,
  Circle,
  Download,
  Eye,
  Grid2x2,
  Grid3x3,
  ExternalLink,
  Maximize2,
  Pause,
  Play,
  Shield,
  Square,
  User,
  Video,
  AlertTriangle,
} from 'lucide-react';

import { useAppSelector } from '@/store/hook';
import { getCctvCamerasPage, getDetectionEventsPage } from '@/services/apis/cctv.api';
import type { CctvCameraVO, DetectionEventVO } from '@/types/cctv.vo';
import type { UserRole } from '@/types/user-role';
import { roleLabels, rolePermissions } from '@/types/user-role';
import { Button } from '@/components/shared/ui/button';
import { Badge } from '@/components/shared/ui/badge';
import { Card } from '@/components/shared/ui/card';
import { ScrollArea } from '@/components/shared/ui/scroll-area';

type LayoutMode = '1x1' | '2x2' | '3x3';
type CategoryKey = 'all' | 'classroom' | 'playground' | 'entrance' | 'corridor' | 'office' | 'parking';

/** UTIC 실시간 교통 CCTV (더미) — 그리드 슬롯 0~3에만 매핑 */
/** iframe 차단 시 새 창 재생용 라벨 (UTIC 구간명) */
const UTIC_DUMMY_SPOT_LABELS = ['원효대교 북단', '국립현충원', '사당역', '영동전화국'] as const;

const UTIC_DUMMY_STREAM_URLS: readonly string[] = [
  'https://www.utic.go.kr/jsp/map/cctvStream.jsp?cctvid=E970150&cctvname=%25EC%259B%2590%25ED%259A%25A8%25EB%258C%2580%25EA%25B5%2590%25EB%25B6%2581%25EB%258B%25A8&kind=EC&cctvip=undefined&cctvch=53&id=424&cctvpasswd=undefined&cctvport=undefined&minX=126.83673788313612&minY=37.4649974459518&maxX=127.12636287326224&maxY=37.585756195539204',
  'https://www.utic.go.kr/jsp/map/cctvStream.jsp?cctvid=L010044&cctvname=%25EA%25B5%25AD%25EB%25A6%25BD%25ED%2598%2584%25EC%25B6%25A9%25EC%259B%2590&kind=Seoul&cctvip=undefined&cctvch=51&id=82&cctvpasswd=undefined&cctvport=undefined&minX=126.83673788313612&minY=37.4649974459518&maxX=127.12636287326224&maxY=37.585756195539204',
  'https://www.utic.go.kr/jsp/map/cctvStream.jsp?cctvid=L010117&cctvname=%25EC%2582%25AC%25EB%258B%25B9%25EC%2597%25AD&kind=Seoul&cctvip=undefined&cctvch=51&id=75&cctvpasswd=undefined&cctvport=undefined&minX=126.83673788313612&minY=37.4649974459518&maxX=127.12636287326224&maxY=37.585756195539204',
  'https://www.utic.go.kr/jsp/map/cctvStream.jsp?cctvid=L010216&cctvname=%25EC%2598%2581%25EB%258F%2599%25EC%25A0%2584%25ED%2599%2594%25EA%25B5%25AD&kind=Seoul&cctvip=undefined&cctvch=52&id=224&cctvpasswd=undefined&cctvport=undefined&minX=126.83673788313612&minY=37.4649974459518&maxX=127.12636287326224&maxY=37.585756195539204',
];

const EVENT_TYPE_LABELS: Record<DetectionEventVO['eventType'], string> = {
  ASSAULT: '폭행',
  FIGHT: '싸움',
  BURGLARY: '절도',
  VANDALISM: '기물파손',
  SWOON: '실신',
  WANDER: '배회',
  TRESPASS: '침입',
  DUMP: '투기',
  ROBBERY: '강도',
  DATEFIGHT: '데이트폭력 및 추행',
  KIDNAP: '납치',
  DRUNKEN: '주취행동',
  OTHER: '기타',
};

function mapCameraLineStatus(
  status: CctvCameraVO['status'],
): 'online' | 'offline' | 'maintenance' {
  switch (status) {
    case 'ACTIVE':
      return 'online';
    case 'PENDING':
      return 'maintenance';
    case 'DISABLED':
    default:
      return 'offline';
  }
}

/** VO에 `isRecording` 없음 → 온라인(`status === 'ACTIVE'`)일 때 REC 표시로 매핑 */
function showRecFromVo(camera: CctvCameraVO): boolean {
  return camera.status === 'ACTIVE';
}

function mapEventUiStatus(
  status: DetectionEventVO['status'],
): 'active' | 'reviewing' | 'resolved' {
  switch (status) {
    case 'OPEN':
    case 'ESCALATED':
      return 'active';
    case 'ACKNOWLEDGED':
    case 'IN_REVIEW':
      return 'reviewing';
    case 'RESOLVED':
    case 'DISMISSED':
    default:
      return 'resolved';
  }
}

function severityLevel(sev: number): 'high' | 'medium' | 'low' {
  if (sev >= 7) return 'high';
  if (sev >= 4) return 'medium';
  return 'low';
}

function severityClasses(level: 'high' | 'medium' | 'low') {
  switch (level) {
    case 'high':
      return 'bg-red-100 text-red-700 border-red-300';
    case 'medium':
      return 'bg-orange-100 text-orange-700 border-orange-300';
    default:
      return 'bg-yellow-100 text-yellow-700 border-yellow-300';
  }
}

function formatRelativeMinutes(iso?: string | null) {
  if (!iso) return '';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '';
  const diffMin = Math.floor((Date.now() - t) / 60000);
  if (diffMin < 1) return '방금 전';
  if (diffMin < 60) return `${diffMin}분 전`;
  const h = Math.floor(diffMin / 60);
  if (h < 24) return `${h}시간 전`;
  return `${Math.floor(h / 24)}일 전`;
}

function formatOverlayTime() {
  return new Date().toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
}

function inferCategoryFromCameraName(name: string): Exclude<CategoryKey, 'all'> {
  const n = name.toLowerCase();
  if (n.includes('문') || n.includes('입구') || n.includes('출입')) return 'entrance';
  if (n.includes('교실') || n.includes('반')) return 'classroom';
  if (n.includes('놀이터') || n.includes('운동장')) return 'playground';
  if (n.includes('복도')) return 'corridor';
  if (n.includes('주차')) return 'parking';
  return 'office';
}

function displayCameraCode(vo: CctvCameraVO): string {
  return `CAM-${String(vo.cameraId).padStart(3, '0')}`;
}

/** 보조 줄: VO에 location 없음 → `serialNo` / `model` */
function displayLocationLine(vo: CctvCameraVO): string {
  return vo.serialNo?.trim() || vo.model?.trim() || '위치 미지정';
}

const ROLE_COLORS: Record<UserRole, string> = {
  SUPERADMIN: 'bg-purple-600',
  PLATFORM_IT_ADMIN: 'bg-indigo-600',
  KINDERGARTEN_ADMIN: 'bg-blue-600',
  TEACHER: 'bg-green-600',
  GUARDIAN: 'bg-orange-600',
};

export function CctvDashboardPage() {
  const router = useRouter();
  const sessionUser = useAppSelector((s) => s.user.user);
  const isAuthenticated = useAppSelector((s) => s.user.isAuthenticated);

  const [cameras, setCameras] = useState<CctvCameraVO[]>([]);
  const [events, setEvents] = useState<DetectionEventVO[]>([]);
  const [loading, setLoading] = useState(true);
  /** API 실패해도 그리드·UTIC 샘플은 그대로 보여 주고, 전체 화면을 막지 않음 */
  const [apiError, setApiError] = useState<string | null>(null);
  const [layout, setLayout] = useState<LayoutMode>('2x2');
  const [categoryFilter, setCategoryFilter] = useState<CategoryKey>('all');
  const [currentPage, setCurrentPage] = useState(0);
  const [isRecording, setIsRecording] = useState(true);
  const [isVideoPaused, setIsVideoPaused] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);

  const effectiveRole: UserRole = sessionUser?.role ?? 'GUARDIAN';
  const displayName = sessionUser?.name?.trim() || sessionUser?.loginId || '게스트';
  const perms = rolePermissions[effectiveRole];

  const load = useCallback(async () => {
    setLoading(true);
    setApiError(null);
    try {
      const [camPage, evPage] = await Promise.all([
        getCctvCamerasPage(0, 200),
        getDetectionEventsPage(0, 300),
      ]);
      setCameras(camPage?.content ?? []);
      setEvents(evPage?.content ?? []);
    } catch (e: unknown) {
      const msg =
        e && typeof e === 'object' && 'response' in e
          ? String((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? '')
          : '';
      setApiError(
        msg ||
          '서버에서 카메라·이벤트 목록을 불러오지 못했습니다. (백엔드 연결·로그인 토큰을 확인해 주세요)',
      );
      setCameras([]);
      setEvents([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const filteredCameras = useMemo(() => {
    if (categoryFilter === 'all') return cameras;
    return cameras.filter((c) => inferCategoryFromCameraName(c.cameraName) === categoryFilter);
  }, [cameras, categoryFilter]);

  const itemsPerPage = layout === '1x1' ? 1 : layout === '2x2' ? 4 : 9;
  const totalPages = Math.max(1, Math.ceil(filteredCameras.length / itemsPerPage));
  const safePage = Math.min(currentPage, totalPages - 1);
  const pageCameras = filteredCameras.slice(safePage * itemsPerPage, safePage * itemsPerPage + itemsPerPage);

  /** API 카메라가 부족해도 그리드 슬롯·UTIC 더미(0~3)는 유지 */
  const paddedGridSlots = useMemo(() => {
    const slots: (CctvCameraVO | null)[] = [...pageCameras];
    while (slots.length < itemsPerPage) {
      slots.push(null);
    }
    return slots;
  }, [pageCameras, itemsPerPage]);

  const eventsByCamera = useMemo(() => {
    const sorted = [...events].sort(
      (a, b) => new Date(b.detectedAt).getTime() - new Date(a.detectedAt).getTime(),
    );
    const m = new Map<number, DetectionEventVO[]>();
    for (const ev of sorted) {
      const list = m.get(ev.cameraId) ?? [];
      if (list.length >= 3) continue;
      list.push(ev);
      m.set(ev.cameraId, list);
    }
    return m;
  }, [events]);

  const activeAlertCount = useMemo(
    () => events.filter((e) => mapEventUiStatus(e.status) === 'active').length,
    [events],
  );

  const cameraStats = useMemo(() => {
    const total = cameras.length;
    const online = cameras.filter((c) => mapCameraLineStatus(c.status) === 'online').length;
    return { total, online, offline: Math.max(0, total - online) };
  }, [cameras]);

  const categoryCounts = useMemo(() => {
    const counts: Record<Exclude<CategoryKey, 'all'>, number> = {
      classroom: 0,
      playground: 0,
      entrance: 0,
      corridor: 0,
      office: 0,
      parking: 0,
    };
    for (const c of cameras) {
      counts[inferCategoryFromCameraName(c.cameraName)] += 1;
    }
    return counts;
  }, [cameras]);

  const gridCols = layout === '1x1' ? 'grid-cols-1' : layout === '2x2' ? 'grid-cols-2' : 'grid-cols-3';

  const adminLike =
    effectiveRole === 'SUPERADMIN' ||
    effectiveRole === 'PLATFORM_IT_ADMIN' ||
    effectiveRole === 'KINDERGARTEN_ADMIN';

  return (
    <div className="flex h-full min-h-0 flex-col bg-gray-50">
      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-0 flex-1 overflow-hidden">
          {/* Sidebar — 피그마 Sidebar.tsx */}
          <div className="flex h-full w-64 shrink-0 flex-col border-r border-gray-200 bg-white">
            <div className="border-b border-gray-200 p-4">
              <div className="mb-1 flex items-center gap-2">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-600">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden>
                    <path
                      d="M12 6C8.5 6 5.5 8 4 11c1.5 3 4.5 5 8 5s6.5-2 8-5c-1.5-3-4.5-5-8-5z"
                      fill="white"
                    />
                    <circle cx="12" cy="11" r="2.5" fill="#7C3AED" />
                  </svg>
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-gray-900">CCTV 모니터링</h2>
                  <p className="text-xs text-gray-500">AI Kids Care</p>
                </div>
              </div>
            </div>

            <div className="border-b border-gray-200 p-4">
              <h3 className="mb-3 text-xs font-semibold uppercase text-gray-500">로그인 정보</h3>
              <Card className={`p-3 text-white ${ROLE_COLORS[effectiveRole]}`}>
                <div className="mb-2 flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-white/20">
                    {adminLike ? <Shield className="h-5 w-5" /> : <User className="h-5 w-5" />}
                  </div>
                  <div>
                    <p className="text-sm font-semibold">{displayName}</p>
                    <p className="text-xs opacity-90">{roleLabels[effectiveRole]}</p>
                  </div>
                </div>
                <div className="border-t border-white/20 pt-2">
                  <p className="text-xs opacity-75">권한 레벨</p>
                  <p className="text-xs font-medium">{roleLabels[effectiveRole]}</p>
                </div>
              </Card>
            </div>

            <div className="border-b border-gray-200 p-4">
              <h3 className="mb-3 text-xs font-semibold uppercase text-gray-500">카메라 현황</h3>
              <Card className="bg-gray-50 p-3">
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-gray-600">전체</span>
                    <Badge variant="secondary">{cameraStats.total}대</Badge>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-gray-600">온라인</span>
                    <Badge className="bg-green-500 hover:bg-green-600">{cameraStats.online}대</Badge>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-gray-600">오프라인</span>
                    <Badge variant="destructive">{cameraStats.offline}대</Badge>
                  </div>
                </div>
              </Card>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto p-4">
              <h3 className="mb-3 text-xs font-semibold uppercase text-gray-500">커버리 목록</h3>
              <div className="space-y-2">
                {(
                  [
                    ['all', '전체', cameraStats.total],
                    ['classroom', '교실', categoryCounts.classroom],
                    ['playground', '놀이터', categoryCounts.playground],
                    ['entrance', '출입구', categoryCounts.entrance],
                    ['corridor', '복도', categoryCounts.corridor],
                    ['office', '사무실/기타', categoryCounts.office],
                    ['parking', '주차장', categoryCounts.parking],
                  ] as const
                ).map(([key, label, count]) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => {
                      setCategoryFilter(key);
                      setCurrentPage(0);
                    }}
                    className={`flex w-full items-center justify-between rounded-lg px-3 py-2 transition-colors ${
                      categoryFilter === key
                        ? 'border border-purple-200 bg-purple-50'
                        : 'bg-gray-50 hover:bg-gray-100'
                    }`}
                  >
                    <span
                      className={`text-sm ${
                        categoryFilter === key ? 'font-medium text-purple-900' : 'text-gray-700'
                      }`}
                    >
                      {label}
                    </span>
                    <Badge
                      className={
                        categoryFilter === 'all' && key === 'all'
                          ? 'bg-purple-600 hover:bg-purple-700'
                          : ''
                      }
                      variant="secondary"
                    >
                      {count}
                    </Badge>
                  </button>
                ))}
              </div>
            </div>

            <div className="border-t border-gray-200 bg-gray-50 p-3">
              <p className="text-center text-xs text-gray-500">
                메뉴·접근 권한은 로그인 역할(<span className="font-medium">{roleLabels[effectiveRole]}</span>)에 따릅니다.
              </p>
            </div>
          </div>

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

            {loading && (
              <div className="flex min-h-[320px] flex-1 items-center justify-center text-sm text-gray-400">
                불러오는 중…
              </div>
            )}

            {!loading && apiError && (
              <div className="mb-4 shrink-0 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
                <p className="font-medium text-amber-900">{apiError}</p>
                <p className="mt-1 text-amber-800/90">
                  아래 교통 CCTV 샘플(UTIC)은 서버와 무관하게 표시됩니다. iframe이 비면 「새 창」으로 열어 보세요.
                </p>
                <div className="mt-2 flex flex-wrap gap-2">
                  <Button type="button" size="sm" variant="outline" onClick={() => void load()}>
                    다시 시도
                  </Button>
                  {!isAuthenticated && (
                    <Button type="button" size="sm" variant="outline" onClick={() => router.push('/')}>
                      홈으로
                    </Button>
                  )}
                </div>
              </div>
            )}

            {!loading && (
              <div className="flex min-h-0 flex-1 flex-col">
                <div className={`grid min-h-0 flex-1 gap-3 ${gridCols}`}>
                  {paddedGridSlots.map((camera, slotIdx) => {
                    const gridSlot = safePage * itemsPerPage + slotIdx;
                    const dummyStreamUrl =
                      gridSlot < UTIC_DUMMY_STREAM_URLS.length
                        ? UTIC_DUMMY_STREAM_URLS[gridSlot]
                        : null;
                    const isPadSlot = camera == null;
                    const camEvents = !isPadSlot
                      ? (eventsByCamera.get(camera.cameraId) ?? [])
                      : [];
                    const lineStatus = !isPadSlot
                      ? mapCameraLineStatus(camera.status)
                      : 'online';
                    const titleName = !isPadSlot
                      ? camera.cameraName
                      : `교통 CCTV · ${UTIC_DUMMY_SPOT_LABELS[gridSlot] ?? `샘플 ${gridSlot + 1}`}`;
                    const subLine = !isPadSlot
                      ? displayLocationLine(camera)
                      : 'UTIC 실시간 (샘플)';
                    const codeLabel = !isPadSlot
                      ? displayCameraCode(camera)
                      : `EXT-${String(gridSlot + 1).padStart(3, '0')}`;
                    const showRecBadge =
                      !isPadSlot &&
                      showRecFromVo(camera) &&
                      lineStatus === 'online';
                    const showLiveBadge =
                      isPadSlot && Boolean(dummyStreamUrl && !isVideoPaused);

                    return (
                      <Card
                        key={!isPadSlot ? camera.cameraId : `pad-${safePage}-${slotIdx}`}
                        className="group relative flex cursor-pointer flex-col gap-0 overflow-hidden p-0 transition-all hover:ring-2 hover:ring-purple-500"
                      >
                        <div className="relative aspect-video min-h-[180px] bg-gray-900">
                          {dummyStreamUrl && !isVideoPaused ? (
                            <iframe
                              title={`교통 CCTV ${gridSlot + 1}`}
                              src={dummyStreamUrl}
                              className="absolute inset-0 z-0 h-full min-h-[180px] w-full border-0"
                              allow="autoplay; fullscreen"
                              loading="lazy"
                              referrerPolicy="no-referrer-when-downgrade"
                            />
                          ) : (
                            <div className="absolute inset-0 z-0 flex items-center justify-center">
                              <Camera className="h-16 w-16 text-gray-700" />
                            </div>
                          )}
                          {dummyStreamUrl && (
                            <a
                              href={dummyStreamUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="absolute right-2 top-10 z-30 inline-flex items-center gap-0.5 rounded bg-black/70 px-1.5 py-0.5 text-[10px] font-medium text-white backdrop-blur-sm hover:bg-black/90"
                            >
                              <ExternalLink className="h-3 w-3 shrink-0" />
                              새 창
                            </a>
                          )}
                          <div className="absolute left-2 top-2 z-10 rounded-md bg-black/80 px-3 py-2 shadow-lg backdrop-blur-md">
                            <div className="text-sm font-semibold leading-tight text-white">
                              {titleName}
                            </div>
                            <div className="mt-0.5 text-xs text-gray-300">{subLine}</div>
                          </div>
                          <div className="absolute right-2 top-2 z-10 rounded bg-black/70 px-2 py-1 font-mono text-xs text-white backdrop-blur-sm">
                            {codeLabel}
                          </div>
                          <div className="absolute bottom-2 left-2 z-10 rounded bg-black/70 px-2 py-1 font-mono text-xs text-white backdrop-blur-sm">
                            {formatOverlayTime()}
                          </div>
                          {showRecBadge && (
                            <div className="absolute bottom-2 right-2 z-10 flex items-center gap-1.5 rounded bg-red-600/90 px-2.5 py-1 shadow-lg backdrop-blur-sm">
                              <Circle className="h-2 w-2 animate-pulse fill-white text-white" />
                              <span className="text-xs font-semibold text-white">REC</span>
                            </div>
                          )}
                          {showLiveBadge && (
                            <div className="absolute bottom-2 right-2 z-10 flex items-center gap-1.5 rounded bg-emerald-600/90 px-2.5 py-1 shadow-lg backdrop-blur-sm">
                              <Circle className="h-2 w-2 animate-pulse fill-white text-white" />
                              <span className="text-xs font-semibold text-white">LIVE</span>
                            </div>
                          )}

                          <div className="absolute inset-0 z-20 flex items-center justify-center gap-2 bg-black/50 opacity-0 transition-opacity group-hover:opacity-100">
                            <Button size="sm" className="bg-white/90 text-gray-900 hover:bg-white" type="button">
                              <Eye className="mr-1 h-4 w-4" />
                              상세보기
                            </Button>
                            <Button
                              size="sm"
                              variant="outline"
                              className="bg-white/90 text-gray-900 hover:bg-white"
                              type="button"
                            >
                              <Maximize2 className="mr-1 h-4 w-4" />
                              전체화면
                            </Button>
                          </div>
                        </div>

                        <div className="min-h-[88px] flex-1 bg-gray-50">
                          {camEvents.length > 0 ? (
                            <div className="space-y-1.5 p-2">
                              {camEvents.map((event) => {
                                const level = severityLevel(event.severity ?? 0);
                                const uiStatus = mapEventUiStatus(event.status);
                                return (
                                  <div
                                    key={event.eventId}
                                    className={`cursor-pointer rounded-md border p-2 transition-all hover:shadow-md ${severityClasses(level)}`}
                                  >
                                    <div className="mb-1 flex items-start justify-between gap-2">
                                      <div className="flex min-w-0 flex-1 items-center gap-1.5">
                                        <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
                                        <span className="truncate text-xs font-semibold">
                                          {EVENT_TYPE_LABELS[event.eventType] ?? event.eventType}
                                        </span>
                                      </div>
                                      {uiStatus === 'active' && (
                                        <Badge variant="destructive" className="text-xs">
                                          진행중
                                        </Badge>
                                      )}
                                      {uiStatus === 'reviewing' && (
                                        <Badge
                                          variant="outline"
                                          className="border-orange-500 text-xs text-orange-700"
                                        >
                                          검토중
                                        </Badge>
                                      )}
                                      {uiStatus === 'resolved' && (
                                        <Badge variant="outline" className="border-gray-400 text-xs text-gray-600">
                                          완료
                                        </Badge>
                                      )}
                                    </div>
                                    <div className="flex items-center justify-between text-[10px]">
                                      <span className="opacity-80">{formatRelativeMinutes(event.detectedAt)}</span>
                                      <span className="opacity-80">
                                        신뢰도 {Math.round(event.confidence ?? 0)}%
                                      </span>
                                    </div>
                                  </div>
                                );
                              })}
                            </div>
                          ) : (
                            <div className="flex h-full min-h-[88px] items-center justify-center p-3">
                              <p className="text-xs text-gray-400">이상 없음</p>
                            </div>
                          )}
                        </div>
                      </Card>
                    );
                  })}
                </div>

                {totalPages > 1 && (
                  <div className="mt-4 flex shrink-0 items-center justify-center gap-4 border-t border-gray-200 py-3">
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={safePage === 0}
                      onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                      type="button"
                    >
                      <ChevronLeft className="h-4 w-4" />
                      이전
                    </Button>
                    <div className="flex items-center gap-2 text-sm text-gray-600">
                      페이지 <span className="font-semibold text-gray-900">{safePage + 1}</span> / {totalPages}
                      <span className="text-gray-400">•</span>
                      총 {filteredCameras.length}대
                    </div>
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={safePage >= totalPages - 1}
                      onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))}
                      type="button"
                    >
                      다음
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                )}
              </div>
            )}
          </main>

          {/* RightPanel — 피그마 RightPanel.tsx */}
          <div className="flex h-full w-80 shrink-0 flex-col border-l border-gray-200 bg-white">
            <div className="border-b border-gray-200 p-4">
              <h2 className="text-sm font-semibold text-gray-900">제어 패널</h2>
              <p className="mt-1 text-xs text-gray-500">카메라 제어 및 설정</p>
            </div>
            <ScrollArea className="min-h-0 flex-1 p-4">
              <div className="space-y-4">
                <Card className="p-4">
                  <h3 className="mb-3 text-sm font-semibold text-gray-900">녹화 제어</h3>
                  <Button
                    type="button"
                    className={`w-full gap-2 ${isRecording ? 'bg-red-600 hover:bg-red-700' : ''}`}
                    onClick={() => setIsRecording((v) => !v)}
                  >
                    {isRecording ? (
                      <>
                        <Pause className="h-4 w-4" />
                        녹화 중지
                      </>
                    ) : (
                      <>
                        <Play className="h-4 w-4" />
                        전체 녹화 시작
                      </>
                    )}
                  </Button>
                  {isRecording && (
                    <div className="mt-3 rounded border border-red-200 bg-red-50 p-2">
                      <div className="flex items-center gap-2 text-xs text-red-700">
                        <Circle className="h-2 w-2 animate-pulse fill-red-500 text-red-500" />
                        <span className="font-medium">녹화 진행 중</span>
                      </div>
                    </div>
                  )}
                </Card>

                <Card className="p-4">
                  <h3 className="mb-3 text-sm font-semibold text-gray-900">화면 레이아웃</h3>
                  <div className="grid grid-cols-3 gap-2">
                    <Button
                      type="button"
                      variant={layout === '1x1' ? 'default' : 'outline'}
                      size="sm"
                      className="flex h-auto flex-col gap-1 py-2"
                      onClick={() => {
                        setLayout('1x1');
                        setCurrentPage(0);
                      }}
                    >
                      <Square className="h-5 w-5" />
                      <span className="text-xs">1×1</span>
                    </Button>
                    <Button
                      type="button"
                      variant={layout === '2x2' ? 'default' : 'outline'}
                      size="sm"
                      className="flex h-auto flex-col gap-1 py-2"
                      onClick={() => {
                        setLayout('2x2');
                        setCurrentPage(0);
                      }}
                    >
                      <Grid2x2 className="h-5 w-5" />
                      <span className="text-xs">2×2</span>
                    </Button>
                    <Button
                      type="button"
                      variant={layout === '3x3' ? 'default' : 'outline'}
                      size="sm"
                      className="flex h-auto flex-col gap-1 py-2"
                      onClick={() => {
                        setLayout('3x3');
                        setCurrentPage(0);
                      }}
                    >
                      <Grid3x3 className="h-5 w-5" />
                      <span className="text-xs">3×3</span>
                    </Button>
                  </div>
                </Card>

                <Card className="p-4">
                  <h3 className="mb-3 text-sm font-semibold text-gray-900">빠른 작업</h3>
                  <div className="space-y-2">
                    <Button variant="outline" size="sm" className="w-full justify-start gap-2" type="button">
                      <Video className="h-4 w-4" />
                      전체 화면 보기
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      className="w-full justify-start gap-2"
                      type="button"
                      onClick={() => setIsVideoPaused((v) => !v)}
                    >
                      {isVideoPaused ? (
                        <>
                          <Play className="h-4 w-4" />
                          재생
                        </>
                      ) : (
                        <>
                          <Pause className="h-4 w-4" />
                          일시정지
                        </>
                      )}
                    </Button>
                    {perms.canExportReports && (
                      <Button variant="outline" size="sm" className="w-full justify-start gap-2" type="button">
                        <Download className="h-4 w-4" />
                        영상 다운로드
                      </Button>
                    )}
                  </div>
                </Card>
              </div>
            </ScrollArea>
          </div>
        </div>

        {/* NotificationList — 피그마 하단 패널 */}
        {showNotifications && (
          <div className="max-h-96 shrink-0 overflow-hidden border-t border-gray-200 bg-gray-50 p-4">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-red-600" />
                <h3 className="font-semibold text-gray-900">전체 이상상황 알림</h3>
                <Badge className="bg-red-500 hover:bg-red-600">{events.length}건</Badge>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="text-gray-500 hover:text-gray-700"
                onClick={() => setShowNotifications(false)}
              >
                닫기
              </Button>
            </div>
            <div className="grid max-h-72 grid-cols-1 gap-3 overflow-y-auto md:grid-cols-2 lg:grid-cols-3">
              {events.length === 0 ? (
                <div className="col-span-full py-8 text-center text-gray-500">이상상황 알림이 없습니다</div>
              ) : (
                events.slice(0, 12).map((event) => {
                  const cam = cameras.find((c) => c.cameraId === event.cameraId);
                  const level = severityLevel(event.severity ?? 0);
                  const uiStatus = mapEventUiStatus(event.status);
                  const borderBg =
                    level === 'high'
                      ? 'border-red-200 bg-red-50'
                      : level === 'medium'
                        ? 'border-orange-200 bg-orange-50'
                        : 'border-yellow-200 bg-yellow-50';
                  return (
                    <Card key={event.eventId} className={`cursor-pointer p-4 hover:shadow-lg ${borderBg}`}>
                      <div className="mb-3 flex items-start justify-between">
                        <div className="flex items-center gap-2">
                          <AlertTriangle className="h-4 w-4 shrink-0 text-red-600" />
                          <h4 className="text-sm font-semibold text-gray-900">
                            {EVENT_TYPE_LABELS[event.eventType]}
                          </h4>
                        </div>
                        <Badge
                          variant={level === 'high' ? 'destructive' : 'secondary'}
                          className="text-xs"
                        >
                          {level === 'high' ? '높음' : level === 'medium' ? '중간' : '낮음'}
                        </Badge>
                      </div>
                      <div className="space-y-2 text-sm">
                        <p className="text-gray-700">
                          {cam?.cameraName ?? `cameraId ${event.cameraId}`}{' '}
                          <span className="text-gray-500">
                            ({cam ? displayLocationLine(cam) : '—'})
                          </span>
                        </p>
                        <p className="text-xs text-gray-600">
                          {formatRelativeMinutes(event.detectedAt)} · 신뢰도{' '}
                          {Math.round(event.confidence ?? 0)}%
                        </p>
                        <div className="flex justify-between border-t border-gray-200 pt-2 text-xs">
                          {uiStatus === 'active' && <Badge variant="destructive">진행중</Badge>}
                          {uiStatus === 'reviewing' && (
                            <Badge variant="outline" className="border-orange-500 text-orange-700">
                              검토중
                            </Badge>
                          )}
                          {uiStatus === 'resolved' && (
                            <Badge variant="outline" className="border-gray-400 text-gray-600">
                              완료
                            </Badge>
                          )}
                        </div>
                      </div>
                    </Card>
                  );
                })
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
