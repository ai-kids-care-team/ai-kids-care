'use client';

import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
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
  Plus,
  Shield,
  Square,
  User,
  Video,
  AlertTriangle,
} from 'lucide-react';

import { useAppSelector } from '@/store/hook';
import {
  getCctvCamerasPage,
  getCameraStreamsPage,
  getDetectionEventTypeCodes,
  getDetectionEventsPage,
} from '@/services/apis/cctv.api';
import { getKindergarten } from '@/services/apis/kindergartens.api';
import { fetchTeacherDisplayNameForUser } from '@/services/apis/teachers.api';
import type { CameraStreamVO } from '@/services/apis/cctv.api';
import type { CctvCameraVO, DetectionEventVO } from '@/types/cctv.vo';
import type { UserRole } from '@/types/user-role';
import { roleLabels, rolePermissions } from '@/types/user-role';
import { Button } from '@/components/shared/ui/button';
import { Badge } from '@/components/shared/ui/badge';
import { Card } from '@/components/shared/ui/card';
import { ScrollArea } from '@/components/shared/ui/scroll-area';

// --- CCTV 데모 스트림: BEGIN (차후 전체 삭제 가능) -----------------------------------------------
/**
 * 임시 교통 CCTV iframe (데모). 영구 데이터 아님.
 * 전부 끄기: `CCTV_TRAFFIC_PLACEHOLDER_ENABLED = false`
 * 카메라 칸만 끄기: `CCTV_CAMERA_TILE_DUMMY_ENABLED = false` (빈 슬롯은 유지)
 *
 * 삭제 후 그리드는 깨지지 않음 — 아래 `resolvePadSlotEmbedUrl` / `resolveRealCameraTileEmbedUrl` /
 * `padSlotTitleName` / `padSlotSubLine` 본문만 안내대로 한 줄씩 바꾸면 API 스트림만 사용.
 */
const CCTV_TRAFFIC_PLACEHOLDER_ENABLED = false;

/** DB에 스트림 URL 없을 때 실제 카메라 타일에 임시 영상 넣기 */
const CCTV_CAMERA_TILE_DUMMY_ENABLED = false;

// DB의 `camera_streams`에서 camera_id=1 MAIN의 http(s) UTIC url을 못 찾는 경우에 대비한 fallback.
// seed `39_camera_streams_seed.sql`의 MAIN https URL을 그대로 사용.
const UTIC_CAMERA1_MAIN_HTTPS_FALLBACK_URL =
  'http://www.ai-kids-care.asia:8082/live/livestream.m3u8';

const TRAFFIC_DEMO_STREAMS = [
  'https://www.utic.go.kr/jsp/map/cctvStream.jsp?cctvid=E970150&cctvname=%25EC%259B%2590%25ED%259A%25A8%25EB%258C%2580%25EA%25B5%2590%25EB%25B6%2581%25EB%258B%25A8&kind=EC&cctvip=undefined&cctvch=53&id=424&cctvpasswd=undefined&cctvport=undefined&minX=126.83673788313612&minY=37.4649974459518&maxX=127.12636287326224&maxY=37.585756195539204',
  'https://www.utic.go.kr/jsp/map/cctvStream.jsp?cctvid=L010044&cctvname=%25EA%25B5%25AD%25EB%25A6%25BD%25ED%2598%2584%25EC%25B6%25A9%25EC%259B%2590&kind=Seoul&cctvip=undefined&cctvch=51&id=82&cctvpasswd=undefined&cctvport=undefined&minX=126.83673788313612&minY=37.4649974459518&maxX=127.12636287326224&maxY=37.585756195539204',
  'https://www.utic.go.kr/jsp/map/cctvStream.jsp?cctvid=L010117&cctvname=%25EC%2582%25AC%25EB%258B%25B9%25EC%2597%25AD&kind=Seoul&cctvip=undefined&cctvch=51&id=75&cctvpasswd=undefined&cctvport=undefined&minX=126.83673788313612&minY=37.4649974459518&maxX=127.12636287326224&maxY=37.585756195539204',
  'https://www.utic.go.kr/jsp/map/cctvStream.jsp?cctvid=L010216&cctvname=%25EC%2598%2581%25EB%258F%2599%25EC%25A0%2584%25ED%2599%2594%25EA%25B5%2590&kind=Seoul&cctvip=undefined&cctvch=52&id=224&cctvpasswd=undefined&cctvport=undefined&minX=126.83673788313612&minY=37.4649974459518&maxX=127.12636287326224&maxY=37.585756195539204',
] as const;

const TRAFFIC_DEMO_LABELS = ['원효대교 북단', '국립현충원', '사당역', '영동전화국'] as const;

function trafficStreamAtSlot(globalSlotIndex: number): string | null {
  if (!CCTV_TRAFFIC_PLACEHOLDER_ENABLED) return null;
  return TRAFFIC_DEMO_STREAMS[globalSlotIndex % TRAFFIC_DEMO_STREAMS.length];
}

function trafficLabelAtSlot(globalSlotIndex: number): string {
  return TRAFFIC_DEMO_LABELS[globalSlotIndex % TRAFFIC_DEMO_LABELS.length];
}

/** API 스트림이 없을 때만 카메라 타일용 URL (슬롯마다 순환) */
function dummyStreamForCameraTile(globalSlotIndex: number, hasApiStream: boolean): string | null {
  if (hasApiStream || !CCTV_CAMERA_TILE_DUMMY_ENABLED || !CCTV_TRAFFIC_PLACEHOLDER_ENABLED) return null;
  return TRAFFIC_DEMO_STREAMS[globalSlotIndex % TRAFFIC_DEMO_STREAMS.length];
}

function buildPadDemoTitle(globalSlotIndex: number): string {
  return `교통 CCTV · ${trafficLabelAtSlot(globalSlotIndex)}`;
}
// --- CCTV 데모 스트림: END -------------------------------------------------------------------

/**
 * 그리드 스트림 URL 진입점 (데모 블록 삭제 시 본문만 교체).
 * 1) resolvePadSlotEmbedUrl → `return null;`
 * 2) resolveRealCameraTileEmbedUrl → `return apiStreamUrl?.trim() || null;`
 * 3) padSlotTitleName → `return '빈 슬롯';`
 * 4) padSlotSubLine → `return '빈 슬롯';` (또는 `'등록 카메라 없음'` 유지)
 */
function resolvePadSlotEmbedUrl(globalSlotIndex: number): string | null {
  return trafficStreamAtSlot(globalSlotIndex);
}

function padSlotTitleName(globalSlotIndex: number, padStreamUrl: string | null): string {
  if (!padStreamUrl) return '빈 슬롯';
  return buildPadDemoTitle(globalSlotIndex);
}

function resolveRealCameraTileEmbedUrl(
  cameraId: number | null,
  globalSlotIndex: number,
  apiStreamUrl: string | null,
): string | null {
  const u = apiStreamUrl?.trim() || null;
  if (u) return u;
  if (cameraId === 1) return UTIC_CAMERA1_MAIN_HTTPS_FALLBACK_URL;
  return dummyStreamForCameraTile(globalSlotIndex, false);
}

function padSlotSubLine(padStreamUrl: string | null): string {
  return padStreamUrl ? '데모 스트림(임시)' : '등록 카메라 없음';
}

type LayoutMode = '1x1' | '2x2' | '3x3';
type CategoryKey =
  | 'all'
  | 'classroom'
  | 'corridor'
  | 'playground'
  | 'dining'
  | 'entrance'
  | 'hall'
  | 'security';

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
  if (n.includes('교실') || n.includes('반')) return 'classroom';
  if (n.includes('복도')) return 'corridor';
  if (n.includes('놀이터') || n.includes('운동장')) return 'playground';
  if (n.includes('식당') || n.includes('급식')) return 'dining';
  if (n.includes('현관') || n.includes('입구') || n.includes('출입') || n.includes('문')) return 'entrance';
  if (n.includes('강당')) return 'hall';
  return 'security';
}

function displayCameraCode(vo: CctvCameraVO): string {
  return `CAM-${String(vo.cameraId).padStart(3, '0')}`;
}

/** 보조 줄: VO에 location 없음 → `serialNo` / `model` */
function displayLocationLine(vo: CctvCameraVO): string {
  return vo.serialNo?.trim() || vo.model?.trim() || '위치 미지정';
}

/** iframe에는 http(s)만 — RTSP 등은 브라우저에서 재생 불가 */
function pickPrimaryStreamUrl(streams: CameraStreamVO[]): string | null {
  const httpStreams = streams.filter((s) => {
    const u = s.streamUrl?.trim();
    if (!u) return false;
    const lower = u.toLowerCase();
    return lower.startsWith('http://') || lower.startsWith('https://');
  });
  if (httpStreams.length === 0) return null;
  const enabled = httpStreams.filter((s) => s.enabled !== false);
  const pool = enabled.length > 0 ? enabled : httpStreams;
  const primary = pool.find((s) => s.isPrimary === true);
  return (primary ?? pool[0]).streamUrl!.trim();
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
  const sessionToken = useAppSelector((s) => s.user.token);

  // Hydration mismatch 방지:
  // SSR에서는 Redux/localStorage가 복구되기 전이라 역할(effectiveRole)이 기본값(GUARDIAN)으로 렌더될 수 있습니다.
  // 첫 클라이언트 렌더에서 역할이 달라지면 아이콘/색상 같은 조건부 렌더가 달라져 경고가 뜹니다.
  // 따라서 UI 표시용 역할은 마운트 이후에만 실제 값으로 전환합니다.
  const [uiHydrated, setUiHydrated] = useState(false);
  useEffect(() => {
    setUiHydrated(true);
  }, []);

  const [cameras, setCameras] = useState<CctvCameraVO[]>([]);
  const [events, setEvents] = useState<DetectionEventVO[]>([]);
  const [loading, setLoading] = useState(true);
  /** API 실패해도 그리드 레이아웃은 유지(데모는 `CCTV 데모 스트림: BEGIN~END` + 진입점 함수) */
  const [eventTypeLabels, setEventTypeLabels] = useState<Record<string, string>>({});
  const [layout, setLayout] = useState<LayoutMode>('2x2');
  const [categoryFilter, setCategoryFilter] = useState<CategoryKey>('all');
  const [currentPage, setCurrentPage] = useState(0);
  const [focusedCameraId, setFocusedCameraId] = useState<number | null>(null);
  const [isVideoPaused, setIsVideoPaused] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  /** 카메라 타일 하단 이상 알림: 기본 3건만 표시, 초과 시 +로 전체 펼침 */
  const [tileAlertsExpandedByCamera, setTileAlertsExpandedByCamera] = useState<Record<number, boolean>>({});
  const [streamMapByCamera, setStreamMapByCamera] = useState<Map<number, CameraStreamVO[]>>(new Map());
  const [selectedCamera, setSelectedCamera] = useState<CctvCameraVO | null>(null);
  /** 현재 구역(`filteredCameras`) 기준 순번 — 전용 전체화면 패널에서만 사용, null이면 패널 미표시 */
  const [quickPlaylistIndex, setQuickPlaylistIndex] = useState<number | null>(null);
  const playlistFullscreenElRef = useRef<HTMLDivElement | null>(null);

  const effectiveRole: UserRole = sessionUser?.role ?? 'GUARDIAN';
  const effectiveRoleForUi: UserRole = uiHydrated ? effectiveRole : 'GUARDIAN';
  const loginIdDisplay =
    sessionUser?.loginId?.trim() || sessionUser?.username?.trim() || (isAuthenticated ? '—' : '게스트');
  const perms = rolePermissions[effectiveRole];
  const decodeJwtKindergartenId = useCallback((token: string | null | undefined): number | null => {
    if (!token) return null;
    try {
      const parts = token.split('.');
      if (parts.length < 2) return null;
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const json = decodeURIComponent(
        atob(base64)
          .split('')
          .map((ch) => `%${ch.charCodeAt(0).toString(16).padStart(2, '0')}`)
          .join(''),
      );
      const payload = JSON.parse(json) as {
        kindergartenId?: number;
        kindergarten_id?: number;
        kgId?: number;
      };
      const v = Number(payload.kindergartenId ?? payload.kindergarten_id ?? payload.kgId ?? 0);
      return Number.isFinite(v) && v > 0 ? v : null;
    } catch {
      return null;
    }
  }, []);
  const inferKindergartenIdFromUserId = useCallback((userIdRaw: unknown): number | null => {
    const userId = Number(userIdRaw);
    if (!Number.isFinite(userId) || userId <= 0) return null;
    if (userId >= 700) return 3;
    if (userId >= 400) return 2;
    if (userId >= 100) return 1;
    return null;
  }, []);
  const sessionKindergartenId = useMemo(() => {
    const direct = Number((sessionUser as { kindergartenId?: number } | null)?.kindergartenId ?? 0);
    if (Number.isFinite(direct) && direct > 0) return direct;
    const fromUserId = inferKindergartenIdFromUserId((sessionUser as { id?: unknown } | null)?.id);
    if (fromUserId != null) return fromUserId;
    const fromSessionToken = decodeJwtKindergartenId(sessionToken);
    if (fromSessionToken != null) return fromSessionToken;
    if (typeof window === 'undefined') return null;
    try {
      const raw = localStorage.getItem('user');
      const parsed = raw ? (JSON.parse(raw) as { kindergartenId?: number }) : null;
      const fromStorageUser = Number(parsed?.kindergartenId ?? 0);
      if (Number.isFinite(fromStorageUser) && fromStorageUser > 0) return fromStorageUser;
      const fromStorageToken = decodeJwtKindergartenId(
        localStorage.getItem('accessToken') ?? localStorage.getItem('token'),
      );
      return fromStorageToken;
    } catch {
      return null;
    }
  }, [decodeJwtKindergartenId, inferKindergartenIdFromUserId, sessionToken, sessionUser]);
  const shouldScopeToOwnKindergarten =
    effectiveRole === 'KINDERGARTEN_ADMIN' || effectiveRole === 'TEACHER';

  const [kindergartenNameResolved, setKindergartenNameResolved] = useState<string | null>(null);
  const [selectedCameraKindergartenName, setSelectedCameraKindergartenName] = useState<string | null>(null);

  useEffect(() => {
    if (sessionKindergartenId == null || sessionKindergartenId <= 0) {
      setKindergartenNameResolved('');
      return;
    }
    let cancelled = false;
    setKindergartenNameResolved(null);
    void getKindergarten(sessionKindergartenId)
      .then((kg) => {
        if (!cancelled) setKindergartenNameResolved(kg.name?.trim() || `유치원 #${sessionKindergartenId}`);
      })
      .catch(() => {
        if (!cancelled) setKindergartenNameResolved(`유치원 #${sessionKindergartenId}`);
      });
    return () => {
      cancelled = true;
    };
  }, [sessionKindergartenId]);

  // 선택된 카메라의 유치원 이름(세션 소속과 무관하게, 해당 카메라가 속한 유치원 기준)
  useEffect(() => {
    const kgId = selectedCamera?.kindergartenId;
    if (!kgId || kgId <= 0) {
      setSelectedCameraKindergartenName('');
      return;
    }
    let cancelled = false;
    setSelectedCameraKindergartenName(null);
    void getKindergarten(kgId)
      .then((kg) => {
        if (!cancelled) setSelectedCameraKindergartenName(kg.name?.trim() || `유치원 #${kgId}`);
      })
      .catch(() => {
        if (!cancelled) setSelectedCameraKindergartenName(`유치원 #${kgId}`);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedCamera?.kindergartenId]);

  const kindergartenAffiliationLabel = useMemo(() => {
    if (sessionKindergartenId != null && sessionKindergartenId > 0) {
      if (kindergartenNameResolved === null) return '불러오는 중…';
      return kindergartenNameResolved || `유치원 #${sessionKindergartenId}`;
    }
    if (effectiveRole === 'SUPERADMIN' || effectiveRole === 'PLATFORM_IT_ADMIN') {
      return '전체(유치원 미지정)';
    }
    return '소속 유치원 없음';
  }, [effectiveRole, kindergartenNameResolved, sessionKindergartenId]);

  /**
   * CCTV 사이드바 두 번째 줄: `teachers.name`만 채움. 유치원 라벨(`kindergartenAffiliationLabel`)과 무관.
   * 교사·원장(유치원) 역할만 조회; 그 외는 Redux `name`만 사용.
   */
  const [teacherTableName, setTeacherTableName] = useState<string | undefined>(undefined);

  useEffect(() => {
    const needsTeacherRow =
      isAuthenticated &&
      (effectiveRole === 'TEACHER' || effectiveRole === 'KINDERGARTEN_ADMIN');
    if (!needsTeacherRow) {
      setTeacherTableName('');
      return;
    }
    const uid = Number(sessionUser?.id);
    if (!Number.isFinite(uid) || uid <= 0 || sessionKindergartenId == null || sessionKindergartenId <= 0) {
      setTeacherTableName('');
      return;
    }
    let cancelled = false;
    setTeacherTableName(undefined);
    void fetchTeacherDisplayNameForUser(uid, sessionKindergartenId).then((n) => {
      if (!cancelled) setTeacherTableName(n ?? '');
    });
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, effectiveRole, sessionUser?.id, sessionKindergartenId]);

  const personNameDisplay = useMemo(() => {
    const fromTeachers = teacherTableName?.trim();
    if (fromTeachers) return fromTeachers;
    return sessionUser?.name?.trim() || '—';
  }, [teacherTableName, sessionUser?.name]);

  const load = useCallback(async () => {
    setLoading(true);
    if (shouldScopeToOwnKindergarten && sessionKindergartenId == null) {
      setCameras([]);
      setEvents([]);
      setLoading(false);
      return;
    }
    const [camResult, evResult, codeResult] = await Promise.allSettled([
      getCctvCamerasPage(0, 200, sessionKindergartenId ?? undefined),
      getDetectionEventsPage(0, 300),
      getDetectionEventTypeCodes(),
    ]);

    const errors: string[] = [];

    if (camResult.status === 'fulfilled') {
      setCameras(camResult.value?.content ?? []);
    } else {
      setCameras([]);
      errors.push('카메라 목록');
    }

    if (evResult.status === 'fulfilled') {
      setEvents(evResult.value?.content ?? []);
    } else {
      setEvents([]);
      errors.push('이벤트 목록');
    }

    if (codeResult.status === 'fulfilled') {
      setEventTypeLabels(
        (codeResult.value ?? []).reduce<Record<string, string>>((acc, row) => {
          const key = String(row.code ?? '').trim().toUpperCase();
          if (!key) return acc;
          acc[key] = row.codeName ?? row.code;
          return acc;
        }, {}),
      );
    } else {
      setEventTypeLabels({});
      errors.push('이벤트 타입 코드');
    }

    if (errors.length > 0) {
      // 에러 카드는 필요 없으므로, 그리드는 계속 렌더되게 두고 콘솔만 남깁니다.
      // (예: 이벤트 목록만 실패해도 카메라 그리드는 표시)
      console.warn(`${errors.join(', ')} 조회에 실패했습니다. 일부 데이터만 표시됩니다.`, errors);
    }
    setLoading(false);
  }, [sessionKindergartenId, shouldScopeToOwnKindergarten]);

  useEffect(() => {
    void load();
  }, [load]);

  const scopedCameras = useMemo(
    () =>
      shouldScopeToOwnKindergarten && sessionKindergartenId != null
        ? cameras.filter((c) => c.kindergartenId === sessionKindergartenId)
        : cameras,
    [cameras, sessionKindergartenId, shouldScopeToOwnKindergarten],
  );

  const filteredCameras = useMemo(() => {
    let base = scopedCameras;
    if (categoryFilter !== 'all') {
      base = base.filter((c) => inferCategoryFromCameraName(c.cameraName) === categoryFilter);
    }
    if (focusedCameraId != null) {
      const single = base.filter((c) => c.cameraId === focusedCameraId);
      if (single.length > 0) return single;
    }
    return base;
  }, [scopedCameras, categoryFilter, focusedCameraId]);

  const scopedEvents = useMemo(() => {
    if (shouldScopeToOwnKindergarten && sessionKindergartenId != null) {
      return events.filter((e) => e.kindergartenId === sessionKindergartenId);
    }
    return events;
  }, [events, sessionKindergartenId, shouldScopeToOwnKindergarten]);

  const itemsPerPage = layout === '1x1' ? 1 : layout === '2x2' ? 4 : 9;
  const totalPages = Math.max(1, Math.ceil(filteredCameras.length / itemsPerPage));
  const safePage = Math.min(currentPage, totalPages - 1);
  const pageCameras = filteredCameras.slice(safePage * itemsPerPage, safePage * itemsPerPage + itemsPerPage);

  /** 전체: 레이아웃만큼 null 패드(임시 교통 스트림으로 빈 칸만 채움). 구역 필터: 해당 카메라만, 패드 없음 */
  const displayGridSlots = useMemo((): (CctvCameraVO | null)[] => {
    if (categoryFilter !== 'all') {
      return [...pageCameras];
    }
    const slots: (CctvCameraVO | null)[] = [...pageCameras];
    // 마지막 페이지에서는 불필요한 패드(교통 데모 타일)가 추가되어 카메라 타일 수가 늘어나는 문제 방지
    while (slots.length < itemsPerPage && safePage !== totalPages - 1) {
      slots.push(null);
    }
    return slots;
  }, [categoryFilter, pageCameras, itemsPerPage, safePage, totalPages]);

  /** 타일에 최신순 전부(상한만) — 화면에는 3건까지, 나머지는 + 펼침 */
  const MAX_TILE_EVENTS_PER_CAMERA = 120;
  const eventsByCamera = useMemo(() => {
    const sorted = [...scopedEvents].sort(
      (a, b) => new Date(b.detectedAt).getTime() - new Date(a.detectedAt).getTime(),
    );
    const m = new Map<number, DetectionEventVO[]>();
    for (const ev of sorted) {
      const list = m.get(ev.cameraId) ?? [];
      if (list.length >= MAX_TILE_EVENTS_PER_CAMERA) continue;
      list.push(ev);
      m.set(ev.cameraId, list);
    }
    return m;
  }, [scopedEvents]);

  const toggleTileAlertsExpanded = useCallback((cameraId: number) => {
    setTileAlertsExpandedByCamera((prev) => ({ ...prev, [cameraId]: !prev[cameraId] }));
  }, []);

  const activeAlertCount = useMemo(
    () => scopedEvents.filter((e) => mapEventUiStatus(e.status) === 'active').length,
    [scopedEvents],
  );

  const cameraStats = useMemo(() => {
    const total = scopedCameras.length;
    const online = scopedCameras.filter((c) => mapCameraLineStatus(c.status) === 'online').length;
    return { total, online, offline: Math.max(0, total - online) };
  }, [scopedCameras]);

  const categoryCounts = useMemo(() => {
    const counts: Record<Exclude<CategoryKey, 'all'>, number> = {
      classroom: 0,
      corridor: 0,
      playground: 0,
      dining: 0,
      entrance: 0,
      hall: 0,
      security: 0,
    };
    for (const c of scopedCameras) {
      counts[inferCategoryFromCameraName(c.cameraName)] += 1;
    }
    return counts;
  }, [scopedCameras]);

  const gridCols = layout === '1x1' ? 'grid-cols-1' : layout === '2x2' ? 'grid-cols-2' : 'grid-cols-3';

  const playlistCamera = useMemo(() => {
    if (
      quickPlaylistIndex == null ||
      quickPlaylistIndex < 0 ||
      quickPlaylistIndex >= filteredCameras.length
    ) {
      return null;
    }
    return filteredCameras[quickPlaylistIndex] ?? null;
  }, [quickPlaylistIndex, filteredCameras]);

  const playlistEmbedUrl = useMemo(() => {
    if (!playlistCamera || quickPlaylistIndex === null) return null;
    const linked = pickPrimaryStreamUrl(streamMapByCamera.get(playlistCamera.cameraId) ?? []);
    return resolveRealCameraTileEmbedUrl(playlistCamera.cameraId, quickPlaylistIndex, linked);
  }, [playlistCamera, quickPlaylistIndex, streamMapByCamera]);

  const playlistSubLine = useMemo(() => {
    if (!playlistCamera || quickPlaylistIndex === null) return '—';
    const linked = pickPrimaryStreamUrl(streamMapByCamera.get(playlistCamera.cameraId) ?? []);
    // "데모(임시)" 라벨은 더미 영상이 실제로 들어간 경우에만 표시
    const dummyFallbackUrl = dummyStreamForCameraTile(quickPlaylistIndex, false);
    const onlyDemo = !(linked?.trim() ?? '') && Boolean(dummyFallbackUrl) && playlistEmbedUrl === dummyFallbackUrl;
    return onlyDemo
      ? `${displayLocationLine(playlistCamera)} · 데모(임시)`
      : displayLocationLine(playlistCamera);
  }, [playlistCamera, quickPlaylistIndex, playlistEmbedUrl, streamMapByCamera]);

  const adminLike =
    effectiveRoleForUi === 'SUPERADMIN' ||
    effectiveRoleForUi === 'PLATFORM_IT_ADMIN' ||
    effectiveRoleForUi === 'KINDERGARTEN_ADMIN';

  const loadCameraStreams = useCallback(async () => {
    try {
      const page = await getCameraStreamsPage(0, 500);
      const source = page?.content ?? [];
      // stream은 iframe 선택 단계에서 `cameraId`로 매칭되므로,
      // 여기서 kindergartenId 필터를 걸면 sessionKindergartenId 산정이 어긋날 때 해당 카메라 stream이 누락될 수 있음.
      // 프론트만 최소 수정으로 확실히 하기 위해 전체를 로드한다.
      const scoped = source;
      const m = new Map<number, CameraStreamVO[]>();
      for (const row of scoped) {
        const list = m.get(row.cameraId) ?? [];
        list.push(row);
        m.set(row.cameraId, list);
      }
      // 빠른 진단: 스트림이 비면 더미/빈슬롯으로 떨어지게 됩니다.
      if (m.size === 0) {
        console.warn('camera_streams loaded but empty (streamMapByCamera size=0)');
      } else {
        const cam1 = m.get(1) ?? [];
        const cam1Primary = pickPrimaryStreamUrl(cam1);
        if (!cam1Primary) {
          console.warn('camera_streams: cameraId=1 has no usable http(s) primary streamUrl', {
            cam1Count: cam1.length,
          });
        }
      }
      setStreamMapByCamera(m);
    } catch (err) {
      console.warn('camera_streams 조회 실패: /camera_streams', err);
      setStreamMapByCamera(new Map());
    }
  }, [sessionKindergartenId, shouldScopeToOwnKindergarten]);

  useEffect(() => {
    void loadCameraStreams();
  }, [loadCameraStreams]);

  const handleOpenDetail = useCallback((camera: CctvCameraVO | null) => {
    if (!camera) return;
    setSelectedCamera(camera);
  }, []);

  const handleCloseDetail = useCallback(() => {
    setSelectedCamera(null);
  }, []);

  useEffect(() => {
    if (typeof document === 'undefined') return;
    const onFullscreenChange = () => {
      const current = document.fullscreenElement as HTMLElement | null;
      // 전체 화면이 완전히 종료된 경우(어떤 요소도 fullscreen이 아닐 때)에만
      // 순회 전체화면 모드를 해제한다. 내부 플레이어(iframe 등)가 자체 fullscreen으로
      // 전환되는 경우는 그대로 유지한다.
      if (!current) {
        setQuickPlaylistIndex(null);
      }
    };
    document.addEventListener('fullscreenchange', onFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', onFullscreenChange);
  }, []);

  useEffect(() => {
    if (quickPlaylistIndex === null || filteredCameras.length > 0) return;
    void document.exitFullscreen?.();
    setQuickPlaylistIndex(null);
  }, [filteredCameras.length, quickPlaylistIndex]);

  useEffect(() => {
    if (quickPlaylistIndex === null || filteredCameras.length === 0) return;
    if (quickPlaylistIndex < filteredCameras.length) return;
    setQuickPlaylistIndex(filteredCameras.length - 1);
  }, [filteredCameras.length, quickPlaylistIndex, filteredCameras]);

  const goQuickPlaylistStep = useCallback(
    (delta: number) => {
      if (quickPlaylistIndex == null || filteredCameras.length === 0) return;
      const nextIdx = quickPlaylistIndex + delta;
      if (nextIdx < 0 || nextIdx >= filteredCameras.length) return;
      setQuickPlaylistIndex(nextIdx);
      setCurrentPage(Math.floor(nextIdx / itemsPerPage));
    },
    [quickPlaylistIndex, filteredCameras, itemsPerPage],
  );

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
              <Card className={`p-3 text-white ${ROLE_COLORS[effectiveRoleForUi]}`}>
                <div className="flex items-start gap-3">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white/20">
                    {adminLike ? <Shield className="h-5 w-5" /> : <User className="h-5 w-5" />}
                  </div>
                  <div className="min-w-0 flex-1 space-y-1">
                    <p suppressHydrationWarning className="truncate text-sm font-semibold">
                      {loginIdDisplay}
                    </p>
                    <p suppressHydrationWarning className="truncate text-sm opacity-95">
                      {personNameDisplay}
                    </p>
                    <p suppressHydrationWarning className="truncate text-sm opacity-95">
                      {kindergartenAffiliationLabel}
                    </p>
                    <p suppressHydrationWarning className="truncate text-sm opacity-95">
                      {roleLabels[effectiveRoleForUi]}
                    </p>
                  </div>
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
              <h3 className="mb-3 text-xs font-semibold uppercase text-gray-500">카메라 목록</h3>
              <div className="space-y-2">
                {(
                  [
                    ['all', '전체', cameraStats.total],
                    ['classroom', '교실', categoryCounts.classroom],
                    ['corridor', '복도', categoryCounts.corridor],
                    ['playground', '놀이터', categoryCounts.playground],
                    ['dining', '식당', categoryCounts.dining],
                    ['entrance', '현관', categoryCounts.entrance],
                    ['hall', '강당', categoryCounts.hall],
                    ['security', '경비실', categoryCounts.security],
                  ] as const
                ).map(([key, label, count]) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => {
                      setCategoryFilter(key);
                      setCurrentPage(0);
                      setFocusedCameraId(null);
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
                <div className="mt-3 border-t border-gray-200 pt-3">
                  <div className="space-y-1.5">
                    {filteredCameras.length === 0 ? (
                      <p className="text-xs text-gray-400">표시할 카메라가 없습니다.</p>
                    ) : (
                      filteredCameras.slice(0, 14).map((c) => {
                        const st = mapCameraLineStatus(c.status);
                        const statusClass =
                          st === 'online'
                            ? 'bg-emerald-100 text-emerald-700'
                            : st === 'maintenance'
                              ? 'bg-amber-100 text-amber-700'
                              : 'bg-gray-200 text-gray-700';
                        const statusLabel = st === 'online' ? '정상' : st === 'maintenance' ? '점검' : '오프라인';
                        const isFocused = focusedCameraId === c.cameraId;
                        return (
                          <div
                            key={`cam-list-${c.cameraId}`}
                            className={`flex cursor-pointer items-center justify-between rounded px-2 py-1.5 transition-colors ${
                              isFocused ? 'bg-purple-50 ring-1 ring-purple-300' : 'bg-gray-50 hover:bg-gray-100'
                            }`}
                            onClick={() => {
                              setFocusedCameraId((prev) => (prev === c.cameraId ? null : c.cameraId));
                              setCurrentPage(0);
                            }}
                          >
                            <div className="min-w-0">
                              <p className="truncate text-xs font-medium text-gray-800">{c.cameraName}</p>
                              <p className="truncate text-[10px] text-gray-500">{displayLocationLine(c)}</p>
                            </div>
                            <Badge className={statusClass}>{statusLabel}</Badge>
                          </div>
                        );
                      })
                    )}
                  </div>
                </div>
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

            {!loading && (
              <div className="flex flex-col gap-4">
                {categoryFilter !== 'all' && filteredCameras.length === 0 ? (
                  <div className="flex min-h-[200px] items-center justify-center rounded-lg border border-dashed border-gray-300 bg-white px-4 text-center text-sm text-gray-500">
                    이 구역에 해당하는 카메라가 없습니다.
                  </div>
                ) : (
                  <div className={`grid w-full items-start gap-3 ${gridCols}`}>
                    {displayGridSlots.map((camera, slotIdx) => {
                    const gridSlot = safePage * itemsPerPage + slotIdx;
                    const isPadSlot = camera == null;
                    const padStream = isPadSlot ? resolvePadSlotEmbedUrl(gridSlot) : null;
                    const cameraId = !isPadSlot ? camera.cameraId : null;
                    const linkedStreamUrl = !isPadSlot
                      ? pickPrimaryStreamUrl(streamMapByCamera.get(camera.cameraId) ?? [])
                      : null;
                    const streamEmbedUrl = isPadSlot
                      ? padStream
                      : resolveRealCameraTileEmbedUrl(cameraId, gridSlot, linkedStreamUrl);

                    // "데모(임시)" 라벨은 더미 스트림(traffic demo)이 실제로 들어간 경우에만 표시
                    const demoFallbackUrl = !isPadSlot ? dummyStreamForCameraTile(gridSlot, false) : null;
                    const usingDemoFallbackOnCamera =
                      !isPadSlot &&
                      streamEmbedUrl != null &&
                      !linkedStreamUrl &&
                      demoFallbackUrl === streamEmbedUrl;
                    const camEvents = !isPadSlot
                      ? (eventsByCamera.get(camera.cameraId) ?? [])
                      : [];
                    const tileAlertsExpanded = !isPadSlot && Boolean(tileAlertsExpandedByCamera[camera.cameraId]);
                    const visibleTileEvents =
                      !isPadSlot && camEvents.length > 0
                        ? tileAlertsExpanded
                          ? camEvents
                          : camEvents.slice(0, 3)
                        : [];
                    const tileAlertsHasMore = !isPadSlot && camEvents.length > 3;
                    const lineStatus = !isPadSlot
                      ? mapCameraLineStatus(camera.status)
                      : 'online';
                    const titleName = !isPadSlot
                      ? camera.cameraName
                      : padSlotTitleName(gridSlot, padStream);
                    const subLine = !isPadSlot
                      ? usingDemoFallbackOnCamera
                        ? `${displayLocationLine(camera)} · 데모(임시)`
                        : displayLocationLine(camera)
                      : padSlotSubLine(padStream);
                    const codeLabel = !isPadSlot
                      ? displayCameraCode(camera)
                      : `EXT-${String(gridSlot + 1).padStart(3, '0')}`;
                    const showLiveBadge = Boolean(
                      streamEmbedUrl &&
                        !isVideoPaused &&
                        (isPadSlot
                          ? Boolean(padStream)
                          : lineStatus === 'online' || usingDemoFallbackOnCamera),
                    );
                    return (
                      <Card
                        key={!isPadSlot ? camera.cameraId : `pad-${safePage}-${slotIdx}`}
                        className="group relative flex w-full cursor-pointer flex-col gap-0 overflow-hidden p-0 transition-all hover:ring-2 hover:ring-purple-500"
                      >
                        <div className="relative min-h-[11rem] w-full shrink-0 overflow-hidden bg-black">
                          {/* aspect-ratio만 두고 자식이 전부 absolute면 높이 0으로 잡히는 브라우저 대응 */}
                          <div className="pointer-events-none block w-full pb-[56.25%]" aria-hidden />
                          {streamEmbedUrl && !isVideoPaused ? (
                            <iframe
                              title={titleName}
                              src={streamEmbedUrl}
                              className="absolute left-0 top-0 z-0 box-border h-full w-full min-h-[1px] border-0"
                              allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
                              referrerPolicy="no-referrer-when-downgrade"
                            />
                          ) : (
                            <div className="absolute inset-0 z-0 flex items-center justify-center bg-gray-900">
                              <Camera className="h-16 w-16 text-gray-700" />
                            </div>
                          )}
                          {streamEmbedUrl && (
                            <a
                              href={streamEmbedUrl}
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
                          {showLiveBadge && (
                            <div className="absolute bottom-2 right-2 z-10 flex items-center gap-1.5 rounded bg-emerald-600/90 px-2.5 py-1 shadow-lg backdrop-blur-sm">
                              <Circle className="h-2 w-2 animate-pulse fill-white text-white" />
                              <span className="text-xs font-semibold text-white">LIVE</span>
                            </div>
                          )}

                          <div className="absolute inset-0 z-20 flex items-center justify-center gap-2 bg-black/50 opacity-0 transition-opacity group-hover:opacity-100">
                            <Button
                              size="sm"
                              className="bg-white/90 text-gray-900 hover:bg-white"
                              type="button"
                              onClick={() => handleOpenDetail(camera)}
                            >
                              <Eye className="mr-1 h-4 w-4" />
                              상세보기
                            </Button>
                            <Button
                              size="sm"
                              variant="outline"
                              className="bg-white/90 text-gray-900 hover:bg-white"
                              type="button"
                              onClick={() => {
                                if (!camera) return;
                                const idx = filteredCameras.findIndex((c) => c.cameraId === camera.cameraId);
                                if (idx < 0) return;
                                setQuickPlaylistIndex(idx);
                                setCurrentPage(Math.floor(idx / itemsPerPage));
                              }}
                            >
                              <Maximize2 className="mr-1 h-4 w-4" />
                              전체화면
                            </Button>
                          </div>
                        </div>

                        <div
                          className={`border-t border-gray-100 bg-gray-50 ${
                            camEvents.length > 0
                              ? `${
                                  tileAlertsExpanded ? 'max-h-72' : 'max-h-32'
                                } min-h-0 shrink-0 overflow-y-auto p-2`
                              : 'shrink-0 px-3 py-2'
                          }`}
                        >
                          {camEvents.length > 0 ? (
                            <div className="space-y-1.5">
                              {visibleTileEvents.map((event) => {
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
                                          {eventTypeLabels[event.eventType] ?? event.eventType}
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
                              {tileAlertsHasMore && !tileAlertsExpanded && (
                                <div className="flex items-center justify-end pt-0.5">
                                  <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    className="h-8 w-8 shrink-0 p-0"
                                    aria-label={`이상 알림 ${camEvents.length - 3}건 더 보기`}
                                    onClick={() => toggleTileAlertsExpanded(camera.cameraId)}
                                  >
                                    <Plus className="h-4 w-4" />
                                  </Button>
                                </div>
                              )}
                              {tileAlertsHasMore && tileAlertsExpanded && (
                                <div className="flex items-center justify-end pt-0.5">
                                  <Button
                                    type="button"
                                    variant="ghost"
                                    size="sm"
                                    className="h-7 text-xs text-gray-600"
                                    onClick={() => toggleTileAlertsExpanded(camera.cameraId)}
                                  >
                                    접기
                                  </Button>
                                </div>
                              )}
                            </div>
                          ) : (
                            <div className="flex items-center justify-center py-0.5">
                              <p className="text-xs text-gray-400">이상 없음</p>
                            </div>
                          )}
                        </div>
                      </Card>
                    );
                  })}
                </div>
                )}

                {totalPages > 1 && (
                  <div className="flex shrink-0 items-center justify-center gap-4 border-t border-gray-200 py-3">
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
                    <Button
                      variant="outline"
                      size="sm"
                      className="w-full justify-start gap-2"
                      type="button"
                      disabled={filteredCameras.length === 0}
                      onClick={() => {
                        if (filteredCameras.length === 0) return;
                        setQuickPlaylistIndex(0);
                        setCurrentPage(0);
                      }}
                    >
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
                    {/* 빠른 작업의 영상 다운로드 버튼은 현재 사용하지 않으므로 숨김 */}
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
                <Badge className="bg-red-500 hover:bg-red-600">{scopedEvents.length}건</Badge>
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
              {scopedEvents.length === 0 ? (
                <div className="col-span-full py-8 text-center text-gray-500">이상상황 알림이 없습니다</div>
              ) : (
                scopedEvents.slice(0, 12).map((event) => {
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
                            {eventTypeLabels[event.eventType] ?? event.eventType}
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
      {quickPlaylistIndex !== null && playlistCamera && (
        <div
          ref={playlistFullscreenElRef}
          id="cctv-playlist-fullscreen-pane"
          className="fixed inset-0 z-[100] flex flex-col bg-black text-white"
        >
          <div className="flex shrink-0 items-center justify-between gap-2 border-b border-white/15 px-3 py-2">
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold">{playlistCamera.cameraName}</p>
              <p className="truncate text-xs text-white/70">{playlistSubLine}</p>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              {playlistEmbedUrl ? (
                <a
                  href={playlistEmbedUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-0.5 rounded bg-white/10 px-2 py-1 text-xs hover:bg-white/20"
                >
                  <ExternalLink className="h-3 w-3" />
                  새 창
                </a>
              ) : null}
              <Button
                type="button"
                size="sm"
                variant="secondary"
                className="bg-white/15 text-white hover:bg-white/25"
                onClick={() => {
                  if (typeof document !== 'undefined') void document.exitFullscreen();
                }}
              >
                닫기
              </Button>
            </div>
          </div>
          <div className="relative w-full bg-black">
            {/* 16:9 비율을 유지하면서 영역을 가득 채우도록 패딩으로 높이를 확보 */}
            <div className="pointer-events-none block w-full pb-[56.25%]" aria-hidden />
            {playlistEmbedUrl && !isVideoPaused ? (
              <iframe
                title={playlistCamera?.cameraName}
                src={playlistEmbedUrl}
                className="absolute inset-0 h-full w-full border-0"
                allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
                referrerPolicy="no-referrer-when-downgrade"
              />
            ) : (
              <div className="absolute inset-0 flex items-center justify-center">
                <Camera className="h-20 w-20 text-gray-600" />
              </div>
            )}
          </div>
          <div
            className="flex shrink-0 items-center justify-center gap-2 border-t border-white/15 bg-black/90 px-3 py-3"
            onClick={(e) => e.stopPropagation()}
          >
            <Button
              type="button"
              size="sm"
              variant="secondary"
              className="bg-white/15 text-white hover:bg-white/25"
              disabled={quickPlaylistIndex <= 0}
              onClick={() => goQuickPlaylistStep(-1)}
            >
              <ChevronLeft className="mr-0.5 h-4 w-4" />
              이전
            </Button>
            <span className="rounded bg-white/10 px-2 py-1.5 text-xs font-medium">
              {quickPlaylistIndex + 1} / {filteredCameras.length}
            </span>
            <Button
              type="button"
              size="sm"
              variant="secondary"
              className="bg-white/15 text-white hover:bg-white/25"
              disabled={quickPlaylistIndex >= filteredCameras.length - 1}
              onClick={() => goQuickPlaylistStep(1)}
            >
              다음
              <ChevronRight className="ml-0.5 h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
      {selectedCamera && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-2xl rounded-xl bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b px-5 py-3">
              <h3 className="text-base font-semibold text-gray-900">카메라 상세정보</h3>
              <Button type="button" size="sm" variant="ghost" onClick={handleCloseDetail}>
                닫기
              </Button>
            </div>
            <div className="space-y-4 p-5">
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <p className="text-gray-500">카메라명</p>
                  <p className="font-medium text-gray-900">{selectedCamera.cameraName}</p>
                </div>
                <div>
                  <p className="text-gray-500">카메라 코드</p>
                  <p className="font-medium text-gray-900">{displayCameraCode(selectedCamera)}</p>
                </div>
                <div>
                  <p className="text-gray-500">유치원</p>
                  <p className="font-medium text-gray-900">
                    {selectedCameraKindergartenName === null
                      ? '불러오는 중…'
                      : selectedCameraKindergartenName || `유치원 #${selectedCamera.kindergartenId}`}
                  </p>
                </div>
                <div>
                  <p className="text-gray-500">상태</p>
                  <p className="font-medium text-gray-900">{selectedCamera.status}</p>
                </div>
              </div>
              <div>
                <p className="mb-2 text-sm font-semibold text-gray-800">스트림 설정 (백엔드 `camera_streams`)</p>
                {(streamMapByCamera.get(selectedCamera.cameraId) ?? []).length === 0 ? (
                  <p className="text-sm text-gray-500">연결된 스트림 설정이 없습니다.</p>
                ) : (
                  <div className="space-y-2">
                {(streamMapByCamera.get(selectedCamera.cameraId) ?? []).map((s, idx) => (
                  <div
                    key={s.streamId != null ? `stream-${s.streamId}` : `stream-${selectedCamera.cameraId}-${idx}`}
                    className="rounded border bg-gray-50 p-2 text-xs"
                  >
                    <p>
                      #{s.streamId ?? idx + 1} · {s.protocol ?? 'UNKNOWN'} · {s.streamType ?? 'N/A'} ·{' '}
                      {s.enabled ? 'ENABLED' : 'DISABLED'}
                    </p>
                    <p className="truncate text-gray-600">{s.streamUrl ?? 'streamUrl 없음'}</p>
                  </div>
                ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
