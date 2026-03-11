export type UserRole = 'super_admin' | 'system_admin' | 'admin' | 'teacher' | 'guardian';

export const roleLabels: Record<UserRole, string> = {
  super_admin: '슈퍼관리자',
  system_admin: '시스템관리자',
  admin: '원장(관리자)',
  teacher: '선생님',
  guardian: '학부모'
};

export type AnomalyType = 'Assault' | 'Fight' | 'Burglary' | 'Vandalism' | 'Swoon' | 'Wander' | 'Trespass' | 'Dump' | 'Robbery' | 'Datefight' | 'Kidnap' | 'Drunken' | 'ALL';

export const anomalyTypeLabels: Record<string, string> = {
  Assault: '폭행', Fight: '싸움', Burglary: '침입', Vandalism: '파손', Swoon: '쓰러짐',
  Wander: '배회', Trespass: '무단침입', Dump: '투기', Robbery: '강도', Datefight: '데이트폭력',
  Kidnap: '납치', Drunken: '주취자', ALL: '전체'
};

export const anomalyTypeColors: Record<string, string> = {
  Assault: 'bg-red-500', Fight: 'bg-red-500', Burglary: 'bg-orange-500', Vandalism: 'bg-orange-500', Swoon: 'bg-red-500',
  Wander: 'bg-yellow-500', Trespass: 'bg-orange-500', Dump: 'bg-yellow-500', Robbery: 'bg-red-500', Datefight: 'bg-red-500',
  Kidnap: 'bg-red-500', Drunken: 'bg-yellow-500', ALL: 'bg-gray-500'
};

export interface AnomalyEvent {
  id: string;
  timestamp: Date;
  cameraId: string;
  cameraName: string;
  type: AnomalyType;
  confidence: number;
  location: string;
  status: 'active' | 'reviewing' | 'resolved';
  severity: 'high' | 'medium' | 'low';
  resolvedBy?: string;
  resolvedAt?: Date;
}

export interface Camera {
  id: string;
  name: string;
  location: string;
  status: 'online' | 'offline' | 'maintenance';
  isRecording: boolean;
  category: 'entrance' | 'classroom' | 'playground' | 'corridor' | 'office' | 'parking';
  streamUrl?: string;
  assignedTeacher?: string;
}

export const rolePermissions: Record<UserRole, any> = {
  super_admin: { canViewAllCameras: true, canResolveAnomaly: true, canExportReports: true, canViewStatistics: true },
  system_admin: { canViewAllCameras: true, canResolveAnomaly: true, canExportReports: true, canViewStatistics: true },
  admin: { canViewAllCameras: true, canResolveAnomaly: true, canExportReports: true, canViewStatistics: true },
  teacher: { canViewAllCameras: false, canViewOwnClassroom: true, canResolveAnomaly: true, canExportReports: false, canViewStatistics: false },
  guardian: { canViewAllCameras: false, canViewOwnClassroom: true, canResolveAnomaly: false, canExportReports: false, canViewStatistics: false }
};