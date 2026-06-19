function firstPositiveLong(...vals: unknown[]): number | null {
  for (const v of vals) {
    if (v === null || v === undefined || v === '') continue;
    const n = typeof v === 'number' ? v : Number(v);
    if (!Number.isFinite(n) || n <= 0) continue;
    const t = Math.trunc(n);
    if (t > 0) return t;
  }
  return null;
}

/** API·레거시 응답에서 편지 PK 추출 (camelCase / snake_case / 문자열 숫자) */
export function resolveAppreciationLetterId(
  row: Record<string, unknown> | { letterId?: number | null; id?: number | null },
): number | null {
  const r = row as Record<string, unknown>;
  return firstPositiveLong(
    r.letterId,
    r.letter_id,
    r.id,
    r.letterID,
  );
}

/** URL 쿼리 `id` → 숫자 (null/undefined 문자열 제외) */
export function parseLetterIdQueryParam(raw: string | null): number | null {
  if (raw == null || raw === '') return null;
  const t = raw.trim();
  if (t === 'null' || t === 'undefined') return null;
  const n = Number(t);
  if (!Number.isFinite(n) || n <= 0) return null;
  return n;
}

export function formatLetterDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd}`;
}

export function formatLetterDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  const hh = String(date.getHours()).padStart(2, '0');
  const mi = String(date.getMinutes()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd} ${hh}:${mi}`;
}

export function targetTypeLabel(t: string): string {
  if (t === 'KINDERGARTEN') return '유치원';
  if (t === 'TEACHER') return '교사';
  return t;
}
