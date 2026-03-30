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
    Pick<Partial<AppreciationLetterVO>, 'senderLoginId' | 'senderGuardianName'>,
): number {
  const list = readEntries();
  const seq = (list.reduce((m, x) => Math.max(m, x.seq), 0) || 0) + 1;
  const ts = nowIso();

  const full: AppreciationLetterVO = {
    letterId: 0, // 서버 PK가 없을 수 있으므로 기본 0
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

