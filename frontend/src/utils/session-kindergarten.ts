/**
 * 로그인 세션에서 "소속 유치원" ID를 복원합니다.
 * Redux `user.kindergartenId` → JWT 클레임 → (보호자만) 데모용 userId 구간 추론 순입니다.
 */

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

export function decodeJwtKindergartenId(token: string | null | undefined): number | null {
  if (!token) return null;
  try {
    const parts = token.split('.');
    if (parts.length < 2) return null;
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((ch) => `%${ch.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join(''),
    );
    const payload = JSON.parse(json) as Record<string, unknown>;
    return firstPositiveLong(
      payload.kindergartenId,
      payload.kindergarten_id,
      payload.kgId,
    );
  } catch {
    return null;
  }
}

/** 시드/데모 계정(user id 구간)과 유치원 매핑 — 백엔드에 kg 클레임이 없을 때 보호자 UI 보조용 */
export function inferKindergartenIdFromDemoUserId(
  userIdRaw: string | number | null | undefined,
): number | null {
  const userId = Number(userIdRaw);
  if (!Number.isFinite(userId) || userId <= 0) return null;
  if (userId >= 700) return 3;
  if (userId >= 400) return 2;
  if (userId >= 100) return 1;
  return null;
}

export function resolveViewerSessionKindergartenId(
  user: { id: string; role?: string; kindergartenId?: number } | null,
  token: string | null | undefined,
): number | null {
  if (!user) return null;
  const direct = firstPositiveLong(user.kindergartenId);
  if (direct != null) return direct;
  const fromJwt = decodeJwtKindergartenId(token ?? null);
  if (fromJwt != null) return fromJwt;
  if (
    user.role === 'GUARDIAN' ||
    user.role === 'TEACHER' ||
    user.role === 'KINDERGARTEN_ADMIN'
  ) {
    return inferKindergartenIdFromDemoUserId(user.id);
  }
  return null;
}
