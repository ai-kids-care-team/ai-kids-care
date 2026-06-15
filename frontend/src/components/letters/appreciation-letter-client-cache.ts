import type { AppreciationLetterVO } from '@/types/appreciationLetter';

const STORAGE_KEY = 'ai_kids_care_appreciation_client_letters_v1';

type StoredEntry = { seq: number; vo: AppreciationLetterVO };

function readEntries(): StoredEntry[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? (parsed as StoredEntry[]) : [];
  } catch {
    return [];
  }
}

function writeEntries(list: StoredEntry[]) {
  if (typeof window === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list.slice(0, 80)));
  } catch {
    /* ignore quota */
  }
}

function nowIso(): string {
  return new Date().toISOString();
}

/**
 * 등록 API 응답에 `letterId`가 누락/비어 있어도 목록·상세에 즉시 표시용.
 * (서버 저장까지는 이루어지지만, 프론트가 리스트에서 PK 매핑을 못 하는 경우 대비)
 */
export function pushClientAppreciationLetter(
  vo: Pick<
    AppreciationLetterVO,
    | 'kindergartenId'
    | 'senderUserId'
    | 'targetType'
    | 'targetId'
    | 'title'
    | 'content'
    | 'isPublic'
    | 'status'
  > &
    Pick<Partial<AppreciationLetterVO>, 'senderLoginId' | 'senderGuardianName'> & { letterId?: number },
): number {
  const list = readEntries();
  const seq = (list.reduce((m, x) => Math.max(m, x.seq), 0) || 0) + 1;
  const ts = nowIso();

  const full: AppreciationLetterVO = {
    letterId:
      vo.letterId != null && Number.isFinite(vo.letterId) && vo.letterId > 0
        ? Math.trunc(vo.letterId)
        : 0,
    kindergartenId: vo.kindergartenId,
    senderUserId: vo.senderUserId,
    targetType: vo.targetType,
    targetId: vo.targetId,
    title: vo.title,
    content: vo.content,
    isPublic: vo.isPublic,
    status: vo.status,
    createdAt: ts,
    updatedAt: ts,
    ...(vo.senderLoginId?.trim() ? { senderLoginId: vo.senderLoginId.trim() } : {}),
    ...(vo.senderGuardianName?.trim()
      ? { senderGuardianName: vo.senderGuardianName.trim() }
      : {}),
  };

  // 최신이 위로 오도록 unshift
  list.unshift({ seq, vo: full });
  writeEntries(list);
  return seq;
}

export function listClientCachedLetters(): StoredEntry[] {
  return readEntries();
}

export function getClientCachedLetterBySeq(seq: number): AppreciationLetterVO | null {
  return readEntries().find((e) => e.seq === seq)?.vo ?? null;
}

export function parseClientLetterSeqParam(raw: string | null): number | null {
  if (raw == null || raw === '') return null;
  const t = raw.trim();
  const n = Number(t);
  if (!Number.isFinite(n) || n <= 0) return null;
  return Math.floor(n);
}

export function removeClientCachedLetter(seq: number): void {
  const list = readEntries().filter((e) => e.seq !== seq);
  writeEntries(list);
}

/** 목록/상세에서 서버 행과 동일하게 쓰는 시그니처(작성 직후 캐시와 API 중복 제거용) */
function clientLetterSignature(
  vo: Pick<AppreciationLetterVO, 'title' | 'senderUserId' | 'targetType' | 'targetId'>,
): string {
  return `${vo.title}|${Number(vo.senderUserId)}|${String(vo.targetType ?? '').toUpperCase()}|${Number(vo.targetId)}`;
}

/**
 * DB에서 삭제한 뒤에도 로컬 캐시에 같은 글이 남으면 목록에 다시 보입니다.
 * 서버 삭제 성공 시 호출해 동일 본문/대상 조합의 캐시 행을 모두 제거합니다.
 */
export function removeClientCachedLettersMatchingLetter(
  vo: Pick<AppreciationLetterVO, 'title' | 'senderUserId' | 'targetType' | 'targetId'>,
): void {
  const sig = clientLetterSignature(vo);
  const list = readEntries().filter((e) => clientLetterSignature(e.vo) !== sig);
  writeEntries(list);
}

/**
 * 서버 삭제 성공 후 보수적으로 캐시를 비우기 위한 보조 함수.
 * 시그니처(title 매칭 등) 불일치 케이스에서도 목록에 남아 보이는 문제를 방지한다.
 */
export function removeClientCachedLettersBySenderUserId(senderUserId: number): void {
  const n = Number(senderUserId);
  if (!Number.isFinite(n) || n <= 0) return;
  const list = readEntries().filter((e) => Number(e.vo.senderUserId) !== n);
  writeEntries(list);
}

export function updateClientCachedLetter(
  seq: number,
  patch: Pick<
    AppreciationLetterVO,
    | 'kindergartenId'
    | 'senderUserId'
    | 'targetType'
    | 'targetId'
    | 'title'
    | 'content'
    | 'isPublic'
    | 'status'
  >,
): boolean {
  const list = readEntries();
  const i = list.findIndex((e) => e.seq === seq);
  if (i < 0) return false;
  const prev = list[i].vo;
  const next: AppreciationLetterVO = {
    ...prev,
    ...patch,
    updatedAt: nowIso(),
  };
  list[i] = { seq, vo: next };
  writeEntries(list);
  return true;
}

