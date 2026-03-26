/** 백엔드 `UserRoleEnum` 과 동일한 값. 로그인 세션·Redux·권한·레이블의 단일 기준 타입. */
export type UserRole =
  | 'GUARDIAN'
  | 'TEACHER'
  | 'KINDERGARTEN_ADMIN'
  | 'PLATFORM_IT_ADMIN'
  | 'SUPERADMIN';

/** UI(데모 역할 전환 등)에서 쓰는 고정 순서 */
export const USER_ROLES: UserRole[] = [
  'SUPERADMIN',
  'PLATFORM_IT_ADMIN',
  'KINDERGARTEN_ADMIN',
  'TEACHER',
  'GUARDIAN',
];

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

/**
 * `/menus?roleType=` 조회용. DB 시드에 원장 전용 행과 ADMIN 행이 분리되어 있어,
 * KINDERGARTEN_ADMIN 세션은 기존처럼 ADMIN 행(대시보드 등)까지 보이게 맞춤.
 */
export function menuApiRoleType(userRole: UserRole): string {
  if (userRole === 'KINDERGARTEN_ADMIN') return 'ADMIN';
  return userRole;
}
