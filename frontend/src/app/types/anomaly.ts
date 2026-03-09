export type UserRole = 
  | 'super_admin'    // 슈퍼 관리자 (개발팀)
  | 'system_admin'   // 시스템 관리자 (IT)
  | 'admin'          // 관리자
  | 'teacher'        // 선생님
  | 'guardian';      // 학부모

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
  'super_admin': '슈퍼 관리자',
  'system_admin': '시스템 관리자',
  'admin': '관리자',
  'teacher': '선생님',
  'guardian': '학부모'
};

export const rolePermissions: Record<UserRole, RolePermissions> = {
  'super_admin': {
    canViewAllCameras: true,
    canViewOwnClassroom: true,
    canManageUsers: true,
    canManageSystem: true,
    canViewAnomalyLog: true,
    canResolveAnomaly: true,
    canExportReports: true,
    canConfigureCameras: true,
    canViewStatistics: true
  },
  'system_admin': {
    canViewAllCameras: true,
    canViewOwnClassroom: true,
    canManageUsers: false,
    canManageSystem: true,
    canViewAnomalyLog: true,
    canResolveAnomaly: true,
    canExportReports: true,
    canConfigureCameras: true,
    canViewStatistics: true
  },
  'admin': {
    canViewAllCameras: true,
    canViewOwnClassroom: true,
    canManageUsers: true,
    canManageSystem: false,
    canViewAnomalyLog: true,
    canResolveAnomaly: true,
    canExportReports: true,
    canConfigureCameras: false,
    canViewStatistics: true
  },
  'teacher': {
    canViewAllCameras: false,
    canViewOwnClassroom: true,
    canManageUsers: false,
    canManageSystem: false,
    canViewAnomalyLog: true,
    canResolveAnomaly: false,
    canExportReports: false,
    canConfigureCameras: false,
    canViewStatistics: false
  },
  'guardian': {
    canViewAllCameras: false,
    canViewOwnClassroom: true,
    canManageUsers: false,
    canManageSystem: false,
    canViewAnomalyLog: true,
    canResolveAnomaly: false,
    canExportReports: false,
    canConfigureCameras: false,
    canViewStatistics: false
  }
};

export type AnomalyType = 
  | 'Assault'      // 폭행
  | 'Fight'        // 싸움
  | 'Burglary'     // 절도
  | 'Vandalism'    // 기물파손
  | 'Swoon'        // 실신
  | 'Wander'       // 배회
  | 'Trespass'     // 침입
  | 'Dump'         // 투기
  | 'Robbery'      // 강도
  | 'Datefight'    // 데이트폭력 및 추행
  | 'Kidnap'       // 납치
  | 'Drunken';     // 주취행동

export interface AnomalyEvent {
  id: string;
  timestamp: Date;
  cameraId: string;
  cameraName: string;
  type: AnomalyType;
  confidence: number; // 0-100
  location: string;
  status: 'active' | 'resolved' | 'reviewing';
  severity: 'high' | 'medium' | 'low';
  resolvedBy?: string;
  resolvedAt?: Date | string;
}

export interface Camera {
  id: string;
  name: string;
  location: string;
  status: 'online' | 'offline' | 'maintenance';
  isRecording: boolean;
  category: 'entrance' | 'classroom' | 'playground' | 'corridor' | 'office' | 'parking';
  assignedTeacher?: string; // For classroom cameras
}

export const anomalyTypeLabels: Record<AnomalyType, string> = {
  'Assault': '폭행',
  'Fight': '싸움',
  'Burglary': '절도',
  'Vandalism': '기물파손',
  'Swoon': '실신',
  'Wander': '배회',
  'Trespass': '침입',
  'Dump': '투기',
  'Robbery': '강도',
  'Datefight': '데이트폭력 및 추행',
  'Kidnap': '납치',
  'Drunken': '주취행동'
};

export const anomalyTypeColors: Record<AnomalyType, string> = {
  'Assault': 'bg-red-600',
  'Fight': 'bg-red-500',
  'Burglary': 'bg-orange-600',
  'Vandalism': 'bg-yellow-600',
  'Swoon': 'bg-purple-600',
  'Wander': 'bg-blue-500',
  'Trespass': 'bg-orange-500',
  'Dump': 'bg-yellow-500',
  'Robbery': 'bg-red-600',
  'Datefight': 'bg-red-700',
  'Kidnap': 'bg-red-700',
  'Drunken': 'bg-amber-600'
};
