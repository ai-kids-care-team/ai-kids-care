import { describe, expect, it } from 'vitest';
import { severityLevel, severityClasses } from './severity';

/**
 * `severityLevel`/`severityClasses` 는 CCTV 대시보드와 감지 이벤트 대시보드가 **동일한**
 * 심각도 분류/배지 스타일을 쓰도록 공유되는 순수 함수(cctv-dashboard-refactor-alerts / C5).
 * 경계값(≥7/≥4)이 두 화면 사이에서 어긋나면 같은 이벤트가 다르게 보이는 회귀가 생긴다.
 */
describe('severityLevel', () => {
  it('classifies 7 and above as high', () => {
    expect(severityLevel(7)).toBe('high');
    expect(severityLevel(10)).toBe('high');
  });

  it('classifies just below 7 as medium (not high)', () => {
    expect(severityLevel(6.9)).toBe('medium');
  });

  it('classifies 4 and above (but below 7) as medium', () => {
    expect(severityLevel(4)).toBe('medium');
    expect(severityLevel(6)).toBe('medium');
  });

  it('classifies just below 4 as low (not medium)', () => {
    expect(severityLevel(3.9)).toBe('low');
  });

  it('classifies below 4 as low', () => {
    expect(severityLevel(0)).toBe('low');
    expect(severityLevel(1)).toBe('low');
  });
});

describe('severityClasses', () => {
  it('returns red classes for high', () => {
    expect(severityClasses('high')).toBe('bg-red-100 text-red-700 border-red-300');
  });

  it('returns orange classes for medium', () => {
    expect(severityClasses('medium')).toBe('bg-orange-100 text-orange-700 border-orange-300');
  });

  it('returns yellow classes for low', () => {
    expect(severityClasses('low')).toBe('bg-yellow-100 text-yellow-700 border-yellow-300');
  });
});
