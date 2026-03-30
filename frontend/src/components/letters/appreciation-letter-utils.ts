import type { AppreciationLetterVO } from '@/types/appreciationLetter';

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

/** 로그인 사용자와 편지 작성자(`sender_user_id`) 동일 여부 — 문자열/숫자 혼용 대응 */
export function isSameAppreciationLetterAuthor(
  userId: string | number | null | undefined,
  senderUserId: string | number | null | undefined,
): boolean {
  if (userId == null || userId === '') return false;
  if (senderUserId == null || senderUserId === '') return false;
  const a = Number(userId);
  const b = Number(senderUserId);
  return Number.isFinite(a) && Number.isFinite(b) && a > 0 && b > 0 && a === b;
}

/** API·스네이크 케이스 혼용 — `false`만 비공개로 본다 */
export function isAppreciationLetterPublic(
  row: { isPublic?: boolean } & Record<string, unknown>,
): boolean {
  const r = row as Record<string, unknown>;
  const v = r.isPublic ?? r.is_public;
  if (v === false || v === 'false' || v === 0) return false;
  return true;
}

/** 비공개 글은 로그인한 작성자 본인만 볼 수 있음(프론트 필터) */
export function viewerMaySeeAppreciationLetter(
  letter: AppreciationLetterVO,
  viewerUserId: string | undefined | null,
  isAuthenticated: boolean,
): boolean {
  if (isAppreciationLetterPublic(letter)) return true;
  if (!isAuthenticated || !viewerUserId) return false;
  return isSameAppreciationLetterAuthor(viewerUserId, letter.senderUserId);
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

export function letterStatusLabel(status: string): string {
  const u = String(status).toUpperCase();
  if (u === 'ACTIVE') return '게시';
  if (u === 'PENDING') return '대기';
  if (u === 'DISABLED') return '비활성';
  return status;
}

export function targetTypeLabel(t: string): string {
  if (t === 'KINDERGARTEN') return '유치원';
  if (t === 'TEACHER') return '교사';
  return t;
}
