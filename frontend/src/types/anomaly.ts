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
  // 👇 이 줄을 추가해 주세요!
  streamUrl?: string | null;
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
