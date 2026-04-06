import type { AppreciationLetterVO } from '@/types/appreciationLetter';
import { resolveViewerSessionKindergartenId } from '@/utils/session-kindergarten';

/** 프론트만으로 유치원 단위 열람 제한 시 사용하는 로그인 사용자 컨텍스트 */
export type AppreciationLetterViewerContext = {
  id: string;
  kindergartenId?: number;
  role?: string;
} | null;

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

/** 편지가 속한 유치원 ID (API camelCase / snake_case) */
export function resolveLetterKindergartenId(
  letter: Pick<AppreciationLetterVO, 'kindergartenId'> | Record<string, unknown>,
): number | null {
  const r = letter as Record<string, unknown>;
  const direct = firstPositiveLong(r.kindergartenId, r.kindergarten_id);
  if (direct != null) return direct;
  const tt = String(r.targetType ?? r.target_type ?? '').toUpperCase();
  if (tt === 'KINDERGARTEN') {
    return firstPositiveLong(r.targetId, r.target_id);
  }
  return null;
}

/** 목록·상세·수정 화면용 시청자 컨텍스트 (토큰에서 유치원 ID 보강) */
export function buildAppreciationLetterViewerContext(
  user: { id: string; role?: string; kindergartenId?: number } | null,
  token: string | null | undefined,
): AppreciationLetterViewerContext {
  if (!user) return null;
  const kg = resolveViewerSessionKindergartenId(user, token);
  return {
    id: user.id,
    kindergartenId: kg ?? undefined,
    role: user.role,
  };
}

/** 전 유치원 열람 허용 역할 (프론트 스코프 우회) */
export function bypassesAppreciationLetterKindergartenScope(
  role: string | null | undefined,
): boolean {
  return role === 'SUPERADMIN' || role === 'PLATFORM_IT_ADMIN';
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

/**
 * 감사편지 열람 가능 여부 (프론트 전용).
 * - 비공개: 작성자 본인만 (유치원 무관)
 * - 공개: 로그인 + 세션의 `kindergartenId`와 편지의 유치원이 같을 때만 (또는 행정/플랫폼 관리자는 전체)
 * - 비로그인: 열람 불가
 */
export function viewerMaySeeAppreciationLetter(
  letter: AppreciationLetterVO,
  viewer: AppreciationLetterViewerContext,
  isAuthenticated: boolean,
): boolean {
  const isPublic = isAppreciationLetterPublic(letter);
  const authorOk =
    isAuthenticated &&
    viewer != null &&
    isSameAppreciationLetterAuthor(viewer.id, letter.senderUserId);

  // 작성자 본인은 공개 여부/유치원 스코프와 무관하게 항상 볼 수 있도록 한다.
  // (목록 UI가 유치원 스코프를 강제해서, 작성 직후 편지가 잠깐이라도 누락되는 문제 방지)
  if (authorOk) {
    return true;
  }

  if (!isPublic) {
    return authorOk;
  }

  if (viewer != null && bypassesAppreciationLetterKindergartenScope(viewer.role)) {
    return true;
  }

  if (!isAuthenticated || viewer == null) {
    return false;
  }

  const letterKg = resolveLetterKindergartenId(letter);
  const viewerKg = viewer.kindergartenId;
  if (letterKg == null) {
    return false;
  }
  if (viewerKg != null && Number.isFinite(viewerKg) && viewerKg > 0) {
    return letterKg === viewerKg;
  }

  return false;
}

/** 목록 행 등 최소 필드만 있을 때 `viewerMaySeeAppreciationLetter`에 넘길 프로브 */
export function buildAppreciationLetterVisibilityProbe(p: {
  isPublic?: boolean;
  senderUserId: number;
  kindergartenId?: number | null;
}): AppreciationLetterVO {
  const kg =
    p.kindergartenId != null && Number.isFinite(p.kindergartenId) && p.kindergartenId > 0
      ? Math.trunc(p.kindergartenId)
      : 0;
  return {
    letterId: 0,
    kindergartenId: kg,
    senderUserId: p.senderUserId,
    targetType: 'KINDERGARTEN',
    targetId: 0,
    title: '',
    content: '',
    isPublic: p.isPublic !== false,
    status: 'ACTIVE',
    createdAt: '',
    updatedAt: '',
  };
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
