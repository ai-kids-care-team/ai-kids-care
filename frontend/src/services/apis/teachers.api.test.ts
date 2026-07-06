import { describe, expect, it } from 'vitest';
import { normalizeTeacherVO, type TeacherApiRow } from './teachers.api';

/**
 * `normalizeTeacherVO`는 백엔드가 camelCase(`TeacherVO`)와 snake_case(레거시 행/그래프
 * 파생 뷰) 두 형태로 필드를 내려줄 수 있는 상황을 흡수하는 계약 정합 지점이다.
 * 여기서 필드명 오정렬(백엔드가 X를 주는데 프론트가 Y를 읽는 것)이 조용히 발생하면
 * 화면에 0/빈 값이 노출된다 — 그 회귀를 잡는다.
 */
describe('normalizeTeacherVO', () => {
  it('reads camelCase fields as-is', () => {
    const raw: TeacherApiRow = {
      teacherId: 10,
      kindergartenId: 3,
      userId: 5,
      name: '김선생',
      gender: 'FEMALE',
      level: 'HEAD',
      startDate: '2026-01-01',
      endDate: null,
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00+09:00',
      updatedAt: '2026-01-02T00:00:00+09:00',
    };

    expect(normalizeTeacherVO(raw)).toEqual({
      teacherId: 10,
      kindergartenId: 3,
      userId: 5,
      name: '김선생',
      gender: 'FEMALE',
      level: 'HEAD',
      startDate: '2026-01-01',
      endDate: null,
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00+09:00',
      updatedAt: '2026-01-02T00:00:00+09:00',
    });
  });

  it('falls back to snake_case fields when camelCase is absent', () => {
    const raw = {
      teacher_id: 11,
      kindergarten_id: 4,
      user_id: 6,
      name: '이선생',
      start_date: '2026-02-01',
      end_date: '2026-03-01',
      created_at: '2026-02-01T00:00:00+09:00',
      updated_at: '2026-02-02T00:00:00+09:00',
    } as unknown as TeacherApiRow;

    const result = normalizeTeacherVO(raw);
    expect(result.teacherId).toBe(11);
    expect(result.kindergartenId).toBe(4);
    expect(result.userId).toBe(6);
    expect(result.startDate).toBe('2026-02-01');
    expect(result.endDate).toBe('2026-03-01');
    expect(result.createdAt).toBe('2026-02-01T00:00:00+09:00');
    expect(result.updatedAt).toBe('2026-02-02T00:00:00+09:00');
  });

  it('applies fallbackKindergartenId only when kindergartenId is missing/non-positive', () => {
    const raw = { teacherId: 1, userId: 2, name: 'x' } as TeacherApiRow;
    const result = normalizeTeacherVO(raw, { fallbackKindergartenId: 99 });
    expect(result.kindergartenId).toBe(99);
  });

  it('does not override a positive kindergartenId with the fallback', () => {
    const raw = { teacherId: 1, kindergartenId: 7, userId: 2, name: 'x' } as TeacherApiRow;
    const result = normalizeTeacherVO(raw, { fallbackKindergartenId: 99 });
    expect(result.kindergartenId).toBe(7);
  });

  it('defaults missing numeric ids to 0 and missing name to empty string', () => {
    const raw = {} as TeacherApiRow;
    const result = normalizeTeacherVO(raw);
    expect(result.teacherId).toBe(0);
    expect(result.kindergartenId).toBe(0);
    expect(result.userId).toBe(0);
    expect(result.name).toBe('');
    expect(result.status).toBeNull();
  });

  it('ignores non-positive / non-numeric id-like values (e.g. 0 or "abc")', () => {
    const raw = {
      teacherId: 0,
      userId: Number.NaN,
      kindergartenId: -5,
      name: 'x',
    } as unknown as TeacherApiRow;
    const result = normalizeTeacherVO(raw);
    expect(result.teacherId).toBe(0);
    expect(result.userId).toBe(0);
    expect(result.kindergartenId).toBe(0);
  });
});
