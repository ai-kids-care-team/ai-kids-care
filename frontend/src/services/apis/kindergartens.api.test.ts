import { describe, expect, it } from 'vitest';
import { normalizeKindergartenPage, normalizeKindergartenVO } from './kindergartens.api';
import type { PageResponse } from './appreciationLetters.api';

/**
 * 백엔드 Spring `Page<KindergartenVO>` ↔ 프론트 `PageResponse<KindergartenVO>` 매핑 +
 * camelCase/snake_case 이중 필드 흡수를 검증한다. 여기서 `content`/`totalElements`/
 * `totalPages`/`number`/`size` 중 하나라도 어긋나면 목록/페이지네이션이 조용히 깨진다.
 */
describe('normalizeKindergartenVO', () => {
  it('reads camelCase fields as-is', () => {
    const raw = {
      kindergartenId: 1,
      name: '해바라기 유치원',
      regionCode: 'SEOUL',
      code: 'K001',
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00+09:00',
      updatedAt: '2026-01-02T00:00:00+09:00',
    };
    expect(normalizeKindergartenVO(raw)).toEqual({
      kindergartenId: 1,
      name: '해바라기 유치원',
      regionCode: 'SEOUL',
      code: 'K001',
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00+09:00',
      updatedAt: '2026-01-02T00:00:00+09:00',
    });
  });

  it('falls back to snake_case fields when camelCase is absent', () => {
    const raw = {
      kindergarten_id: 2,
      name: '민들레 유치원',
      region_code: 'BUSAN',
      created_at: '2026-02-01T00:00:00+09:00',
      updated_at: '2026-02-02T00:00:00+09:00',
    };
    const result = normalizeKindergartenVO(raw);
    expect(result.kindergartenId).toBe(2);
    expect(result.regionCode).toBe('BUSAN');
    expect(result.createdAt).toBe('2026-02-01T00:00:00+09:00');
    expect(result.updatedAt).toBe('2026-02-02T00:00:00+09:00');
  });

  it('defaults a missing/non-positive id to 0 and a blank name to the em dash placeholder', () => {
    const result = normalizeKindergartenVO({});
    expect(result.kindergartenId).toBe(0);
    expect(result.name).toBe('—');
  });
});

describe('normalizeKindergartenPage', () => {
  it('maps every row via normalizeKindergartenVO while preserving Spring Page pagination fields', () => {
    const page: PageResponse<unknown> = {
      content: [
        { kindergartenId: 1, name: 'A' },
        { kindergarten_id: 2, name: 'B' },
      ],
      totalElements: 2,
      totalPages: 1,
      size: 20,
      number: 0,
      first: true,
      last: true,
    };

    const result = normalizeKindergartenPage(page);

    expect(result.totalElements).toBe(2);
    expect(result.totalPages).toBe(1);
    expect(result.size).toBe(20);
    expect(result.number).toBe(0);
    expect(result.first).toBe(true);
    expect(result.last).toBe(true);
    expect(result.content).toHaveLength(2);
    expect(result.content[0].kindergartenId).toBe(1);
    expect(result.content[1].kindergartenId).toBe(2);
  });

  it('treats a null/undefined content array as empty rather than throwing', () => {
    const page = {
      content: undefined,
      totalElements: 0,
      totalPages: 0,
      size: 20,
      number: 0,
      first: true,
      last: true,
    } as unknown as PageResponse<unknown>;

    expect(normalizeKindergartenPage(page).content).toEqual([]);
  });
});
