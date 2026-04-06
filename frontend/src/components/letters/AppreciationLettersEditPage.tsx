'use client';

import type { FormEvent } from 'react';
import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Heart, List } from 'lucide-react';
import { toast } from 'sonner';
import {
  getAppreciationLetterDetail,
  updateAppreciationLetter,
} from '@/services/apis/appreciationLetters.api';
import { getKindergarten } from '@/services/apis/kindergartens.api';
import {
  getTeacher,
  normalizeTeacherVO,
  type TeacherApiRow,
  type TeacherVO,
} from '@/services/apis/teachers.api';
import type { KindergartenVO } from '@/services/apis/kindergartens.api';
import type { AppreciationLetterVO, AppreciationTargetType } from '@/types/appreciationLetter';
import { useAppSelector } from '@/store/hook';
import { index as appStore } from '@/store/index';
import { openLoginModal } from '@/utils/auth-modal';
import { GuardianAuthorCard } from './GuardianAuthorCard';
import { LetterTargetPicker } from './LetterTargetPicker';
import { getApiErrorMessage } from './api-error-message';
import {
  getClientCachedLetterBySeq,
  parseClientLetterSeqParam,
  updateClientCachedLetter,
} from './appreciation-letter-client-cache';
import {
  buildAppreciationLetterViewerContext,
  isSameAppreciationLetterAuthor,
  parseLetterIdQueryParam,
  resolveAppreciationLetterId,
  viewerMaySeeAppreciationLetter,
} from './appreciation-letter-utils';
import { canWriteAppreciationLetters } from '@/types/user-role';
import { resolveViewerSessionKindergartenId } from '@/utils/session-kindergarten';

export function AppreciationLettersEditPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const clientSeq = parseClientLetterSeqParam(searchParams.get('cid'));
  const id = parseLetterIdQueryParam(searchParams.get('id')) ?? NaN;
  const { user, token, isAuthenticated } = useAppSelector((state) => state.user);
  const senderNum = user?.id != null ? Number(user.id) : NaN;

  const [loading, setLoading] = useState(true);
  const [targetType, setTargetType] = useState<AppreciationTargetType>('KINDERGARTEN');
  const [kindergartenId, setKindergartenId] = useState<number | null>(null);
  const [targetId, setTargetId] = useState<number | null>(null);
  const [targetLabel, setTargetLabel] = useState('');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isPublic, setIsPublic] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [loadError, setLoadError] = useState('');
  /** DB에 저장된 작성자 — 수정 요청 시 그대로 전송(FK·일치 보장) */
  const [storedSenderUserId, setStoredSenderUserId] = useState<number | null>(null);
  const [presetKgForTeacherPicker, setPresetKgForTeacherPicker] = useState<{
    kindergartenId: number;
    name: string;
  } | null>(null);
  /** 상세 API 응답의 `letterId`(없으면 URL `id`) — PUT 경로에 사용 */
  const [serverLetterId, setServerLetterId] = useState<number | null>(null);

  const canEdit = useMemo(
    () => Boolean(isAuthenticated && user && token && Number.isFinite(senderNum)),
    [isAuthenticated, user, token, senderNum],
  );

  const guardianLockKg = useMemo(() => {
    if (!user || !canWriteAppreciationLetters(user.role)) return null;
    return resolveViewerSessionKindergartenId(user, token);
  }, [user, token]);

  const hasTarget = kindergartenId != null && targetId != null && targetLabel !== '';

  const handleTargetTypeChange = (t: AppreciationTargetType) => {
    setTargetType(t);
    setKindergartenId(null);
    setTargetId(null);
    setTargetLabel('');
  };

  const handleSelectKindergarten = (row: KindergartenVO) => {
    setTargetType('KINDERGARTEN');
    setKindergartenId(row.kindergartenId);
    setTargetId(row.kindergartenId);
    setTargetLabel(row.name);
  };

  const handleSelectTeacher = (row: TeacherVO) => {
    const n = normalizeTeacherVO(row as TeacherApiRow);
    setTargetType('TEACHER');
    setKindergartenId(n.kindergartenId);
    setTargetId(n.teacherId);
    setTargetLabel(`${n.name} (교사)`);
  };

  const handleClearLetterTarget = () => {
    setKindergartenId(null);
    setTargetId(null);
    setTargetLabel('');
    setPresetKgForTeacherPicker(null);
  };

  useEffect(() => {
    /* Redux·localStorage 복원 전에 effect가 먼저 돌면 user가 비어 권한 오류가 난다 → user.id가 있을 때만 검증·폼 채움 */
    const currentUser = user;
    if (!currentUser?.id) {
      setStoredSenderUserId(null);
      setServerLetterId(null);
      if (typeof window !== 'undefined') {
        const stored = window.localStorage.getItem('user');
        const tok =
          window.localStorage.getItem('accessToken') ?? window.localStorage.getItem('token');
        if (stored && tok) {
          return;
        }
      }
      setLoading(false);
      setLoadError('');
      return;
    }

    if (!canWriteAppreciationLetters(currentUser.role)) {
      setLoading(false);
      setLoadError('감사 편지는 보호자(학부모) 계정만 수정할 수 있습니다.');
      setStoredSenderUserId(null);
      setServerLetterId(null);
      return;
    }

    // 1) 작성 직후 캐시 글(?cid) 로딩
    if (clientSeq != null) {
      const loadClient = async () => {
        setLoading(true);
        setLoadError('');
        setServerLetterId(null);
        const row = getClientCachedLetterBySeq(clientSeq);
        if (!row) {
          setLoadError('감사 편지를 찾을 수 없습니다. 목록에서 다시 열어 주세요.');
          setStoredSenderUserId(null);
          setLoading(false);
          return;
        }
        const viewerCtx = buildAppreciationLetterViewerContext(currentUser, token);
        if (!viewerMaySeeAppreciationLetter(row as AppreciationLetterVO, viewerCtx, isAuthenticated)) {
          setLoadError('소속 유치원의 감사 편지만 열람·수정할 수 있습니다.');
          setStoredSenderUserId(null);
          setLoading(false);
          return;
        }
        if (!isSameAppreciationLetterAuthor(currentUser.id, row.senderUserId)) {
          setLoadError(
            '수정 권한이 없습니다. 이 편지를 작성한 회원 계정으로 로그인했는지 확인해 주세요.',
          );
          setStoredSenderUserId(null);
          setLoading(false);
          return;
        }

        setStoredSenderUserId(row.senderUserId);
        setKindergartenId(row.kindergartenId);
        const tt = String(row.targetType ?? '').toUpperCase();
        setTargetType(tt === 'TEACHER' ? 'TEACHER' : 'KINDERGARTEN');
        setTargetId(row.targetId);
        setTitle(row.title);
        setContent(row.content);
        setIsPublic(row.isPublic !== false);

        try {
          if (tt === 'TEACHER') {
            const t = await getTeacher(row.targetId);
            setTargetLabel(`${t.name} (교사)`);
            try {
              const k = await getKindergarten(row.kindergartenId);
              setPresetKgForTeacherPicker({ kindergartenId: k.kindergartenId, name: k.name });
            } catch {
              setPresetKgForTeacherPicker({
                kindergartenId: row.kindergartenId,
                name: `유치원 #${row.kindergartenId}`,
              });
            }
          } else {
            const k = await getKindergarten(row.targetId);
            setTargetLabel(k.name);
            setPresetKgForTeacherPicker(null);
          }
        } catch {
          setTargetLabel(
            tt === 'TEACHER' ? `교사 #${row.targetId}` : `유치원 #${row.targetId}`,
          );
          if (tt === 'TEACHER') {
            setPresetKgForTeacherPicker({
              kindergartenId: row.kindergartenId,
              name: `유치원 #${row.kindergartenId}`,
            });
          } else {
            setPresetKgForTeacherPicker(null);
          }
        } finally {
          setLoading(false);
        }
      };
      void loadClient();
      return;
    }

    // 2) API 글(?id) 로딩
    if (!Number.isFinite(id) || id <= 0) {
      setLoadError('유효하지 않은 ID입니다.');
      setLoading(false);
      setStoredSenderUserId(null);
      setServerLetterId(null);
      return;
    }

    const load = async () => {
      setLoading(true);
      setLoadError('');
      setServerLetterId(null);
      try {
        const row = await getAppreciationLetterDetail(id);
        const meSlice = appStore.getState().user;
        const me = meSlice.user;
        const meAuthed = meSlice.isAuthenticated;
        const meCtx = buildAppreciationLetterViewerContext(me, meSlice.token);
        if (!viewerMaySeeAppreciationLetter(row, meCtx, meAuthed)) {
          setLoadError('소속 유치원의 감사 편지만 열람·수정할 수 있습니다.');
          setStoredSenderUserId(null);
          return;
        }
        if (!isSameAppreciationLetterAuthor(me?.id, row.senderUserId)) {
          console.warn(
            '[감사편지 수정] 작성자 불일치',
            { letterSenderUserId: row.senderUserId, loggedInUserId: me?.id },
          );
          setLoadError(
            '수정 권한이 없습니다. 이 편지를 작성한 회원 계정으로 로그인했는지 확인해 주세요.',
          );
          setStoredSenderUserId(null);
          return;
        }

        setStoredSenderUserId(row.senderUserId);
        setKindergartenId(row.kindergartenId);
        const tt = String(row.targetType ?? '').toUpperCase();
        setTargetType(tt === 'TEACHER' ? 'TEACHER' : 'KINDERGARTEN');
        setTargetId(row.targetId);
        setTitle(row.title);
        setContent(row.content);
        setIsPublic(row.isPublic !== false);

        const pk = resolveAppreciationLetterId(row as unknown as Record<string, unknown>);
        const resolved = pk != null && pk > 0 ? pk : id;
        setServerLetterId(Number.isFinite(resolved) && resolved > 0 ? Math.trunc(resolved) : null);

        try {
          if (tt === 'TEACHER') {
            const t = await getTeacher(row.targetId);
            setTargetLabel(`${t.name} (교사)`);
            try {
              const k = await getKindergarten(row.kindergartenId);
              setPresetKgForTeacherPicker({ kindergartenId: k.kindergartenId, name: k.name });
            } catch {
              setPresetKgForTeacherPicker({
                kindergartenId: row.kindergartenId,
                name: `유치원 #${row.kindergartenId}`,
              });
            }
          } else {
            const k = await getKindergarten(row.targetId);
            setTargetLabel(k.name);
            setPresetKgForTeacherPicker(null);
          }
        } catch {
          setTargetLabel(
            tt === 'TEACHER' ? `교사 #${row.targetId}` : `유치원 #${row.targetId}`,
          );
          if (tt === 'TEACHER') {
            setPresetKgForTeacherPicker({
              kindergartenId: row.kindergartenId,
              name: `유치원 #${row.kindergartenId}`,
            });
          } else {
            setPresetKgForTeacherPicker(null);
          }
        }
      } catch (e) {
        console.warn('감사 편지 불러오기 실패:', e);
        setLoadError('감사 편지를 불러오지 못했습니다.');
        setStoredSenderUserId(null);
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [id, clientSeq, user?.id, user?.role, token, isAuthenticated]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canEdit || loadError) return;

    if (!hasTarget || kindergartenId == null || targetId == null) {
      toast.error('감사 대상을 다시 선택해 주세요.');
      return;
    }
    if (!title.trim() || !content.trim()) {
      toast.error('제목과 내용을 입력해주세요.');
      return;
    }

    const senderForApi = storedSenderUserId ?? senderNum;
    if (!Number.isFinite(senderForApi) || senderForApi <= 0) {
      toast.error('회원 정보가 올바르지 않습니다. 다시 로그인한 뒤 수정해 주세요.');
      return;
    }

    setSubmitting(true);
    try {
      if (clientSeq != null) {
        const ok = updateClientCachedLetter(clientSeq, {
          kindergartenId,
          senderUserId: senderForApi,
          targetType,
          targetId,
          title: title.trim(),
          content: content.trim(),
          isPublic,
          status: 'ACTIVE',
        });
        if (!ok) {
          toast.error('저장에 실패했습니다. 목록에서 다시 열어 주세요.');
          return;
        }
        toast.success('수정되었습니다.');
        router.push(`/letters/read?cid=${clientSeq}`);
        return;
      }

      const putId =
        serverLetterId != null && Number.isFinite(serverLetterId) && serverLetterId > 0
          ? serverLetterId
          : id;
      if (!Number.isFinite(putId) || putId <= 0) {
        toast.error('저장할 편지 ID를 찾을 수 없습니다. 목록에서 다시 열어 주세요.');
        return;
      }
      await updateAppreciationLetter(putId, {
        kindergartenId,
        senderUserId: senderForApi,
        targetType,
        targetId,
        title: title.trim(),
        content: content.trim(),
        isPublic,
        status: 'ACTIVE',
      });
      toast.success('수정되었습니다.');
      router.push(`/letters/read?id=${putId}`);
    } catch (err) {
      console.warn('감사 편지 수정 실패:', err);
      toast.error(getApiErrorMessage(err, '수정에 실패했습니다.'));
    } finally {
      setSubmitting(false);
    }
  };

  if (!canEdit && !loading) {
    return (
      <div className="min-h-screen bg-gray-50 px-4 py-4 sm:px-5 sm:py-5">
        <main className="mx-auto max-w-[38.4rem]">
          <div className="rounded-2xl bg-white p-6 shadow-lg text-center">
            <p className="mb-4 text-sm text-slate-600">로그인이 필요합니다.</p>
            <button
              type="button"
              onClick={() => openLoginModal()}
              className="rounded-lg bg-[#006b52] px-5 py-2 text-white hover:bg-[#005640]"
            >
              로그인
            </button>
            <div className="mt-4">
              <Link href="/letters" className="text-sm text-[#006b52] hover:underline">
                목록으로
              </Link>
            </div>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-4 sm:px-5 sm:py-5">
      <main className="mx-auto max-w-[38.4rem]">
        <div className="mb-4">
          <Link
            href={
              clientSeq != null
                ? `/letters/read?cid=${clientSeq}`
                : Number.isFinite(id) && id > 0
                  ? `/letters/read?id=${id}`
                  : '/letters'
            }
            className="inline-flex items-center gap-2 text-sm text-[#006b52] transition-colors hover:text-[#005640]"
          >
            <List className="h-4 w-4" />
            상세로
          </Link>
        </div>

        <div className="rounded-2xl bg-white p-6 shadow-lg">
          <div className="mb-6 flex items-center gap-2.5 border-b border-gray-200 pb-5">
            <Heart className="h-6 w-6 text-[#006b52]" />
            <h2 className="text-xl font-semibold tracking-tight">감사 편지 수정</h2>
          </div>

          {loading && <p className="py-10 text-center text-sm text-gray-500">불러오는 중입니다.</p>}

          {!loading && loadError && (
            <p className="rounded-lg bg-red-50 p-4 text-sm text-red-600">{loadError}</p>
          )}

          {!loading && !loadError && canEdit && user && (
            <form onSubmit={(ev) => void handleSubmit(ev)} className="space-y-5">
              <GuardianAuthorCard heading="작성자 (수정 불가)" />

              <LetterTargetPicker
                targetType={targetType}
                onTargetTypeChange={handleTargetTypeChange}
                onSelectKindergarten={handleSelectKindergarten}
                onSelectTeacher={handleSelectTeacher}
                selectedDisplayText={targetLabel}
                hasSelection={hasTarget}
                onClearTarget={handleClearLetterTarget}
                presetKindergartenForTeacherFlow={presetKgForTeacherPicker}
                lockedKindergartenId={guardianLockKg}
              />

              <p className="text-xs text-gray-500">저장 시 상태는 항상 <strong className="text-slate-700">게시(ACTIVE)</strong>로 맞춰집니다.</p>

              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">제목</label>
                <input
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  required
                />
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">내용</label>
                <textarea
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  rows={8}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  required
                />
              </div>

              <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  checked={!isPublic}
                  onChange={(e) => setIsPublic(!e.target.checked)}
                />
                비공개 (나만 보기)
              </label>
              <p className="text-xs text-slate-500">
                체크하면 목록·상세에서 로그인한 작성자 본인에게만 보입니다.
              </p>

              <div className="flex justify-end gap-2 border-t border-gray-100 pt-5">
                <Link
                  href={
                    clientSeq != null
                      ? `/letters/read?cid=${clientSeq}`
                      : `/letters/read?id=${id}`
                  }
                  className="rounded-lg border border-gray-300 px-4 py-2 text-sm text-slate-700 hover:bg-gray-50"
                >
                  취소
                </Link>
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-lg bg-[#006b52] px-4 py-2 text-sm text-white hover:bg-[#005640] disabled:opacity-50"
                >
                  {submitting ? '저장 중…' : '저장'}
                </button>
              </div>
            </form>
          )}
        </div>
      </main>
    </div>
  );
}
