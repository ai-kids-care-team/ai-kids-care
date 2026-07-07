import type { UserRole } from '@/types/user-role';

/**
 * ADR-0013: 상단 네비게이션 메뉴를 (퇴역한) 백엔드 `menu` 딕셔너리 테이블 / `/api/v1/menus`
 * 대신 프론트엔드 정적 설정으로 공급한다. 메뉴는 라우팅·표시 구성(path/label/role)이라
 * 본래 프론트엔드 책임이며, ADR-0013 도 menu → 프론트 정적 설정으로 명시한다.
 *
 * 역할별 노출은 기존 `db/initdb/02_menu.sql` 의 5개 메뉴 × role_type 행을 그대로 옮긴 것.
 */
export type MenuItem = {
  /** 안정적 식별 키 (구 menu_key) — React key 및 디버깅용 */
  key: string;
  label: string;
  path: string;
  /** 정렬 순서 (구 sort_order) */
  sortOrder: number;
};

/** 세션이 없을 때(비로그인) 사용하는 의사 역할. 구 menu.role_type = 'ANONYMOUS'. */
export type MenuRole = UserRole | 'ANONYMOUS';

const HOME: MenuItem = { key: 'HOME', label: '홈', path: '/', sortOrder: 5 };
const CCTV_CAMERAS: MenuItem = { key: 'CCTV_CAMERAS', label: '대시보드', path: '/cctvCamera', sortOrder: 10 };
const DETECTION_EVENT: MenuItem = { key: 'DETECTION_EVENT', label: '이상 탐지', path: '/detectionEvents', sortOrder: 15 };
const APPRECIATION_LETTER: MenuItem = { key: 'APPRECIATION_LETTER', label: '감사편지', path: '/letters', sortOrder: 20 };
const ANNOUNCEMENTS: MenuItem = { key: 'ANNOUNCEMENTS', label: '공지사항', path: '/announcements', sortOrder: 25 };
/** INT-01: 알림 수신함 — GUARDIAN / TEACHER / KINDERGARTEN_ADMIN 대상 */
const NOTIFICATIONS: MenuItem = { key: 'NOTIFICATIONS', label: '알림', path: '/notifications', sortOrder: 30 };
/**
 * UX-04: 원 가입 승인함 — SPEC-0002 Slice A `AdminKindergartenController`.
 * 백엔드 거친 권한 게이트는 role=KINDERGARTEN_ADMIN(+ tenant identity)만 확인하고,
 * level(DIRECTOR/VICE_DIRECTOR) 세밀 검사는 서버가 403으로 별도 강제한다.
 */
const KINDERGARTEN_APPROVALS: MenuItem = {
  key: 'KINDERGARTEN_APPROVALS',
  label: '가입 승인함',
  path: '/admin/kindergarten/approvals',
  sortOrder: 12,
};
/**
 * UX-04: 슈퍼관리자 가입 승인함 — SPEC-0002 Slice B `AdminPlatformController`.
 * 백엔드가 PLATFORM scope + role=PLATFORM_IT_ADMIN 만 허용(SUPERADMIN 본인은 승인 불가).
 */
const PLATFORM_APPROVALS: MenuItem = {
  key: 'PLATFORM_APPROVALS',
  label: '슈퍼관리자 승인함',
  path: '/admin/platform/approvals',
  sortOrder: 12,
};
/**
 * UX-05 (director-operations-ui, C6): 원장 운영 관리 — 학급/교실/카메라 스트림 CRUD.
 * `OperationsManagementPage` 가 role===KINDERGARTEN_ADMIN 이 아니면 자체적으로도 안내만
 * 보여주지만, 다른 역할 메뉴에는 애초에 이 항목을 넣지 않는다(백엔드 `@PreAuthorize` 가 최종 게이트).
 */
const KINDERGARTEN_OPERATIONS: MenuItem = {
  key: 'KINDERGARTEN_OPERATIONS',
  label: '운영 관리',
  path: '/admin/kindergarten/operations',
  sortOrder: 13,
};
/**
 * wire-orphan-management-uis (UX-01): AI 모델 관리 대장 — 기존 `/api/v1/ai_models`
 * (PLATFORM_METADATA_READ/WRITE) 를 처음으로 프론트에 접선한다. READ 는 PLATFORM_IT_ADMIN +
 * SUPERADMIN 둘 다 허용되므로(`AuthorizationPolicy`), 두 역할 메뉴에 모두 추가한다 — WRITE
 * 컨트롤 노출 여부는 `AiModelsManagementPage` 가 role 로 자체 게이트.
 */
const AI_MODELS: MenuItem = {
  key: 'AI_MODELS',
  label: 'AI 모델 관리',
  path: '/admin/platform/ai-models',
  sortOrder: 14,
};
/**
 * wire-orphan-management-uis (UX-02): 관계 그래프 조회 — 기존 `graph.api.ts`/`ChildGraphViewer`
 * (`GET /api/v1/graph/children/{id}`, `GET /api/v1/graph/teachers/{id}`) 는 이미 구현돼 있었으나
 * 메뉴 진입점이 없어 raw ID 를 아는 사람만 URL 을 직접 쳐야 도달 가능했다(orphan UI). GUARDIAN 은
 * 추가하지 않는다(design 범위 — 보호자용 그래프 열람 UX 는 별도 논의).
 */
const CHILD_GRAPH: MenuItem = {
  key: 'CHILD_GRAPH',
  label: '관계 그래프',
  path: '/graph',
  sortOrder: 16,
};

/**
 * 역할별 메뉴 (02_menu.sql 의 role_type 행과 1:1 대응 + UX-04 승인함 신규 추가).
 * - HOME / ANNOUNCEMENTS: 전 역할 + ANONYMOUS
 * - CCTV_CAMERAS: KINDERGARTEN_ADMIN (TEACHER는 UX-03으로 제거됨 — 백엔드가 교사 조회를 거부)
 * - DETECTION_EVENT: SUPERADMIN, KINDERGARTEN_ADMIN, TEACHER, GUARDIAN
 * - APPRECIATION_LETTER: KINDERGARTEN_ADMIN, TEACHER, GUARDIAN
 * - NOTIFICATIONS: GUARDIAN, TEACHER, KINDERGARTEN_ADMIN, SUPERADMIN (수신자 역할; ANONYMOUS 제외)
 * - KINDERGARTEN_APPROVALS: KINDERGARTEN_ADMIN 전용(신규, UX-04)
 * - PLATFORM_APPROVALS: PLATFORM_IT_ADMIN 전용(신규, UX-04)
 * - KINDERGARTEN_OPERATIONS: KINDERGARTEN_ADMIN 전용(신규, UX-05) — 학급/교실/카메라 스트림 관리
 * - AI_MODELS: PLATFORM_IT_ADMIN(전체 CRUD) + SUPERADMIN(조회 전용) (신규, wire-orphan-management-uis/UX-01)
 * - CHILD_GRAPH: KINDERGARTEN_ADMIN, TEACHER (신규, wire-orphan-management-uis/UX-02) — GUARDIAN 제외
 */
export const MENU_BY_ROLE: Record<MenuRole, MenuItem[]> = {
  ANONYMOUS: [HOME, ANNOUNCEMENTS],
  PLATFORM_IT_ADMIN: [HOME, PLATFORM_APPROVALS, AI_MODELS, ANNOUNCEMENTS],
  SUPERADMIN: [HOME, DETECTION_EVENT, AI_MODELS, ANNOUNCEMENTS, NOTIFICATIONS],
  KINDERGARTEN_ADMIN: [
    HOME,
    CCTV_CAMERAS,
    DETECTION_EVENT,
    KINDERGARTEN_APPROVALS,
    KINDERGARTEN_OPERATIONS,
    CHILD_GRAPH,
    APPRECIATION_LETTER,
    ANNOUNCEMENTS,
    NOTIFICATIONS,
  ],
  // UX-03: 백엔드가 TEACHER 의 camera/stream/detection 조회를 전부 거부하므로
  // (CctvDashboardPage.tsx canViewLiveStreams === 'KINDERGARTEN_ADMIN' 하드 게이트),
  // CCTV_CAMERAS 진입점을 노출하면 교사가 매번 빈 "접근 권한 없음" 패널에서 막힌다 — 저비용 완화로 메뉴에서 제거.
  TEACHER: [HOME, DETECTION_EVENT, CHILD_GRAPH, APPRECIATION_LETTER, ANNOUNCEMENTS, NOTIFICATIONS],
  GUARDIAN: [HOME, DETECTION_EVENT, APPRECIATION_LETTER, ANNOUNCEMENTS, NOTIFICATIONS],
};

/** 역할의 메뉴를 sortOrder 순으로 반환. 미지의 역할은 ANONYMOUS 로 폴백. */
export function getMenusForRole(role: MenuRole | string | null | undefined): MenuItem[] {
  const items = (role != null && role in MENU_BY_ROLE)
    ? MENU_BY_ROLE[role as MenuRole]
    : MENU_BY_ROLE.ANONYMOUS;
  return [...items].sort((a, b) => a.sortOrder - b.sortOrder);
}
