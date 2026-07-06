/**
 * CCTV 대시보드(`components/cctv`)의 순수 포맷/분류 헬퍼 — 렌더링/상태와 무관해
 * `CctvDashboardPage`에서 분리했다(cctv-dashboard-refactor-alerts / C5, QLT-01+ARC-04).
 */

import type { CctvCameraVO } from '@/types/cctv.vo';

export type CctvCategoryKey =
  | 'all'
  | 'classroom'
  | 'corridor'
  | 'playground'
  | 'dining'
  | 'entrance'
  | 'hall'
  | 'security';

export function formatRelativeMinutes(iso?: string | null): string {
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

export function formatOverlayTime(): string {
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

export function inferCategoryFromCameraName(name: string): Exclude<CctvCategoryKey, 'all'> {
  const n = name.toLowerCase();
  if (n.includes('교실') || n.includes('반')) return 'classroom';
  if (n.includes('복도')) return 'corridor';
  if (n.includes('놀이터') || n.includes('운동장')) return 'playground';
  if (n.includes('식당') || n.includes('급식')) return 'dining';
  if (n.includes('현관') || n.includes('입구') || n.includes('출입') || n.includes('문')) return 'entrance';
  if (n.includes('강당')) return 'hall';
  return 'security';
}

export function displayCameraCode(vo: CctvCameraVO): string {
  return `CAM-${String(vo.cameraId).padStart(3, '0')}`;
}

/** 보조 줄: VO에 location 없음 → `serialNo` / `model` */
export function displayLocationLine(vo: CctvCameraVO): string {
  return vo.serialNo?.trim() || vo.model?.trim() || '위치 미지정';
}
