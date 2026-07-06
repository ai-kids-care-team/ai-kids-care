import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  displayCameraCode,
  displayLocationLine,
  formatRelativeMinutes,
  inferCategoryFromCameraName,
} from './cctvFormat';
import type { CctvCameraVO } from '@/types/cctv.vo';

function makeCamera(overrides: Partial<CctvCameraVO> = {}): CctvCameraVO {
  return {
    cameraId: 1,
    kindergartenId: 1,
    serialNo: null,
    cameraName: '1반 교실',
    model: null,
    createdByUserId: 1,
    status: 'ACTIVE',
    lastSeenAt: null,
    createdAt: '2026-01-01T00:00:00+09:00',
    updatedAt: '2026-01-01T00:00:00+09:00',
    ...overrides,
  };
}

describe('formatRelativeMinutes', () => {
  const NOW = new Date('2026-07-06T12:00:00+09:00').getTime();

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns empty string for a falsy input', () => {
    expect(formatRelativeMinutes(undefined)).toBe('');
    expect(formatRelativeMinutes(null)).toBe('');
  });

  it('returns empty string for an unparsable date', () => {
    expect(formatRelativeMinutes('not-a-date')).toBe('');
  });

  it('renders "방금 전" for less than a minute ago', () => {
    expect(formatRelativeMinutes(new Date(NOW - 30_000).toISOString())).toBe('방금 전');
  });

  it('renders minutes for under an hour', () => {
    expect(formatRelativeMinutes(new Date(NOW - 5 * 60_000).toISOString())).toBe('5분 전');
  });

  it('renders hours for under a day', () => {
    expect(formatRelativeMinutes(new Date(NOW - 3 * 3600_000).toISOString())).toBe('3시간 전');
  });

  it('renders days at 24h and beyond', () => {
    expect(formatRelativeMinutes(new Date(NOW - 25 * 3600_000).toISOString())).toBe('1일 전');
  });
});

describe('displayCameraCode', () => {
  it('zero-pads the camera id to 3 digits with a CAM- prefix', () => {
    expect(displayCameraCode(makeCamera({ cameraId: 7 }))).toBe('CAM-007');
    expect(displayCameraCode(makeCamera({ cameraId: 123 }))).toBe('CAM-123');
  });
});

describe('displayLocationLine', () => {
  it('prefers serialNo when present', () => {
    expect(displayLocationLine(makeCamera({ serialNo: 'SN-1', model: 'M1' }))).toBe('SN-1');
  });

  it('falls back to model when serialNo is blank/absent', () => {
    expect(displayLocationLine(makeCamera({ serialNo: null, model: 'M1' }))).toBe('M1');
    expect(displayLocationLine(makeCamera({ serialNo: '   ', model: 'M1' }))).toBe('M1');
  });

  it('falls back to a placeholder when both are blank/absent', () => {
    expect(displayLocationLine(makeCamera({ serialNo: null, model: null }))).toBe('위치 미지정');
  });
});

describe('inferCategoryFromCameraName', () => {
  it.each([
    ['1반 교실', 'classroom'],
    ['2층 복도', 'corridor'],
    ['놀이터 A', 'playground'],
    ['운동장 서편', 'playground'],
    ['급식실', 'dining'],
    ['정문 현관', 'entrance'],
    ['강당', 'hall'],
    ['알수없는 이름', 'security'],
  ] as const)('categorizes %s as %s', (name, expected) => {
    expect(inferCategoryFromCameraName(name)).toBe(expected);
  });
});
