/**
 * 감지 이벤트 심각도 분류/배지 스타일 — CCTV 대시보드(`components/cctv`)와 감지 이벤트
 * 대시보드(`components/detectionEvents/DetectionEventsDashboard`)가 **공유**하는 단일
 * 진실원(cctv-dashboard-refactor-alerts / C5, QLT-01+ARC-04). 두 화면이 각자 로컬 함수로
 * 중복 구현하면 분류 경계(≥7/≥4)가 어긋나는 회귀가 생길 수 있어 이 모듈로 일원화한다.
 */

export type SeverityLevel = 'high' | 'medium' | 'low';

export function severityLevel(sev: number): SeverityLevel {
  if (sev >= 7) return 'high';
  if (sev >= 4) return 'medium';
  return 'low';
}

export function severityClasses(level: SeverityLevel): string {
  switch (level) {
    case 'high':
      return 'bg-red-100 text-red-700 border-red-300';
    case 'medium':
      return 'bg-orange-100 text-orange-700 border-orange-300';
    default:
      return 'bg-yellow-100 text-yellow-700 border-yellow-300';
  }
}
