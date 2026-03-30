/**
 * 백엔드 `com.ai_kids_care.v1.type.UserRoleEnum` 과 동일한 문자열.
 * 로그인 세션·Redux·권한·레이블·`/api/v1/menus?roleType=`(DB `menu.role_type`) 의 단일 기준.
 */
export type UserRole =
  | 'GUARDIAN'
  | 'TEACHER'
  | 'KINDERGARTEN_ADMIN'
  | 'PLATFORM_IT_ADMIN'
  | 'SUPERADMIN';

/** 공지사항 글쓰기·수정·삭제 UI (GUARDIAN 제외) */
export const ANNOUNCEMENT_EDITOR_ROLES: readonly UserRole[] = [
  'TEACHER',
  'KINDERGARTEN_ADMIN',
  'PLATFORM_IT_ADMIN',
  'SUPERADMIN',
];

export function canManageAnnouncements(role: UserRole | string | null | undefined): boolean {
  if (role == null || role === '') return false;
  return (ANNOUNCEMENT_EDITOR_ROLES as readonly string[]).includes(role);
}

/** 감사 편지 작성·수정·삭제(본인 글) — 보호자(학부모)만 */
export function canWriteAppreciationLetters(role: UserRole | string | null | undefined): boolean {
  return role === 'GUARDIAN';
}

/** 공지 작성·수정 안내 문구용 — 기술 역할명 대신 화면용 한글명만 나열 */
export function describeAnnouncementEditorRolesKorean(): string {
  return ANNOUNCEMENT_EDITOR_ROLES.map((r) => roleLabels[r]).join('·');
}

export interface RolePermissions {
  canViewAllCameras: boolean;
  canViewOwnClassroom: boolean;
  canManageUsers: boolean;
  canManageSystem: boolean;
  canViewAnomalyLog: boolean;
  canResolveAnomaly: boolean;
  canExportReports: boolean;
  canConfigureCameras: boolean;
  canViewStatistics: boolean;
}

export const roleLabels: Record<UserRole, string> = {
  SUPERADMIN: '행정청',
  PLATFORM_IT_ADMIN: '시스템 관리자',
  KINDERGARTEN_ADMIN: '유치원 원장',
  TEACHER: '선생님',
  GUARDIAN: '학부모',
};

export const rolePermissions: Record<UserRole, RolePermissions> = {
  SUPERADMIN: {
    canViewAllCameras: true,
    canViewOwnClassroom: true,
    canManageUsers: true,
    canManageSystem: true,
    canViewAnomalyLog: true,
    canResolveAnomaly: true,
    canExportReports: true,
    canConfigureCameras: true,
    canViewStatistics: true,
  },
  PLATFORM_IT_ADMIN: {
    canViewAllCameras: true,
    canViewOwnClassroom: true,
    canManageUsers: false,
    canManageSystem: true,
    canViewAnomalyLog: true,
    canResolveAnomaly: true,
    canExportReports: true,
    canConfigureCameras: true,
    canViewStatistics: true,
  },
  KINDERGARTEN_ADMIN: {
    canViewAllCameras: true,
    canViewOwnClassroom: true,
    canManageUsers: true,
    canManageSystem: false,
    canViewAnomalyLog: true,
    canResolveAnomaly: true,
    canExportReports: true,
    canConfigureCameras: false,
    canViewStatistics: true,
  },
  TEACHER: {
    canViewAllCameras: false,
    canViewOwnClassroom: true,
    canManageUsers: false,
    canManageSystem: false,
    canViewAnomalyLog: true,
    canResolveAnomaly: false,
    canExportReports: false,
    canConfigureCameras: false,
    canViewStatistics: false,
  },
  GUARDIAN: {
    canViewAllCameras: false,
    canViewOwnClassroom: true,
    canManageUsers: false,
    canManageSystem: false,
    canViewAnomalyLog: true,
    canResolveAnomaly: false,
    canExportReports: false,
    canConfigureCameras: false,
    canViewStatistics: false,
  },
};
