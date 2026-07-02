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
const CCTV_CAMERAS: MenuItem = { key: 'CCTV_CAMERAS', label: '대시보드', path: '/cctvCameras', sortOrder: 10 };
const DETECTION_EVENT: MenuItem = { key: 'DETECTION_EVENT', label: '이상 탐지', path: '/detectionEvents', sortOrder: 15 };
const APPRECIATION_LETTER: MenuItem = { key: 'APPRECIATION_LETTER', label: '감사편지', path: '/letters', sortOrder: 20 };
const ANNOUNCEMENTS: MenuItem = { key: 'ANNOUNCEMENTS', label: '공지사항', path: '/announcements', sortOrder: 25 };
/** INT-01: 알림 수신함 — GUARDIAN / TEACHER / KINDERGARTEN_ADMIN 대상 */
const NOTIFICATIONS: MenuItem = { key: 'NOTIFICATIONS', label: '알림', path: '/notifications', sortOrder: 30 };

/**
 * 역할별 메뉴 (02_menu.sql 의 role_type 행과 1:1 대응).
 * - HOME / ANNOUNCEMENTS: 전 역할 + ANONYMOUS
 * - CCTV_CAMERAS: KINDERGARTEN_ADMIN, TEACHER
 * - DETECTION_EVENT: SUPERADMIN, KINDERGARTEN_ADMIN, TEACHER, GUARDIAN
 * - APPRECIATION_LETTER: KINDERGARTEN_ADMIN, TEACHER, GUARDIAN
 * - NOTIFICATIONS: GUARDIAN, TEACHER, KINDERGARTEN_ADMIN, SUPERADMIN (수신자 역할; ANONYMOUS 제외)
 */
export const MENU_BY_ROLE: Record<MenuRole, MenuItem[]> = {
  ANONYMOUS: [HOME, ANNOUNCEMENTS],
  PLATFORM_IT_ADMIN: [HOME, ANNOUNCEMENTS],
  SUPERADMIN: [HOME, DETECTION_EVENT, ANNOUNCEMENTS, NOTIFICATIONS],
  KINDERGARTEN_ADMIN: [HOME, CCTV_CAMERAS, DETECTION_EVENT, APPRECIATION_LETTER, ANNOUNCEMENTS, NOTIFICATIONS],
  TEACHER: [HOME, CCTV_CAMERAS, DETECTION_EVENT, APPRECIATION_LETTER, ANNOUNCEMENTS, NOTIFICATIONS],
  GUARDIAN: [HOME, DETECTION_EVENT, APPRECIATION_LETTER, ANNOUNCEMENTS, NOTIFICATIONS],
};

/** 역할의 메뉴를 sortOrder 순으로 반환. 미지의 역할은 ANONYMOUS 로 폴백. */
export function getMenusForRole(role: MenuRole | string | null | undefined): MenuItem[] {
  const items = (role != null && role in MENU_BY_ROLE)
    ? MENU_BY_ROLE[role as MenuRole]
    : MENU_BY_ROLE.ANONYMOUS;
  return [...items].sort((a, b) => a.sortOrder - b.sortOrder);
}
