'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Heart, List, Pencil, Trash2, User } from 'lucide-react';
import { toast } from 'sonner';
import {
  deleteAppreciationLetter,
  getAppreciationLetterDetail,
} from '@/services/apis/appreciationLetters.api';
import type { AppreciationLetterVO } from '@/types/appreciationLetter';
import {
  buildAppreciationLetterViewerContext,
  formatLetterDateTime,
  isAppreciationLetterPublic,
  isSameAppreciationLetterAuthor,
  letterStatusLabel,
  parseLetterIdQueryParam,
  resolveAppreciationLetterId,
  resolveLetterKindergartenId,
  targetTypeLabel,
  viewerMaySeeAppreciationLetter,
} from './appreciation-letter-utils';
import { getGuardianByLoginId, getGuardianByUserId } from '@/services/apis/guardians.api';
import { getLoginIdByUserId } from '@/services/apis/usersPublic.api';
import { getKindergarten } from '@/services/apis/kindergartens.api';
import { getTeacher } from '@/services/apis/teachers.api';
import { useAppSelector } from '@/store/hook';
import { canWriteAppreciationLetters } from '@/types/user-role';
import { getApiErrorMessage } from './api-error-message';

export function AppreciationLettersDetailPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user, isAuthenticated } = useAppSelector((state) => state.user);
  const id = parseLetterIdQueryParam(searchParams.get('id')) ?? NaN;
  const [hydrated, setHydrated] = useState(false);
  const [letter, setLetter] = useState<AppreciationLetterVO | null>(null);
  const [resolvedLetterId, setResolvedLetterId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [error, setError] = useState('');
  const [senderGuardianName, setSenderGuardianName] = useState<string | null>(null);
  const [senderLoginId, setSenderLoginId] = useState<string | null>(null);
  const [targetSummary, setTargetSummary] = useState('');
  const [metaLoading, setMetaLoading] = useState(false);

  useEffect(() => {
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (!deleteConfirmOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !deleting) setDeleteConfirmOpen(false);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [deleteConfirmOpen, deleting]);

  /** DB/URL 기준 편지 PK — 응답에 `letterId`가 없을 때도 URL·상태로 보강 */
  const apiLetterId = useMemo(() => {
    const fromVo =
      letter != null ? resolveAppreciationLetterId(letter as unknown as Record<string, unknown>) : null;
    const candidates = [
      resolvedLetterId,
      Number.isFinite(id) && id > 0 ? Math.trunc(id) : null,
      fromVo != null && fromVo > 0 ? fromVo : null,
    ];
    for (const c of candidates) {
      if (c != null && Number.isFinite(c) && c > 0) return Math.trunc(c);
    }
    return null;
  }, [letter, resolvedLetterId, id]);

  const canEdit = useMemo(
    () =>
      Boolean(
        isAuthenticated &&
          letter &&
          user &&
          apiLetterId != null &&
          canWriteAppreciationLetters(user.role) &&
          isSameAppreciationLetterAuthor(user.id, letter.senderUserId),
      ),
    [isAuthenticated, letter, user, apiLetterId],
  );

  const resolvedAuthorLoginLabel = useMemo(() => {
    if (metaLoading) return null;
    const fromApi = senderLoginId?.trim();
    if (fromApi) return fromApi;
    if (user && letter && isSameAppreciationLetterAuthor(user.id, letter.senderUserId)) {
      const fromStore = (user.loginId || user.username || '').trim();
      if (fromStore) return fromStore;
      const idStr = String(user.id ?? '').trim();
      if (idStr && !/^\d+$/.test(idStr)) return idStr;
    }
    return null;
  }, [metaLoading, senderLoginId, user, letter]);

  useEffect(() => {
    if (!letter) {
      setSenderGuardianName(null);
      setSenderLoginId(null);
      setTargetSummary('');
      setMetaLoading(false);
      return;
    }
    let cancelled = false;
    setMetaLoading(true);
    setSenderGuardianName(null);
    setSenderLoginId(null);
    setTargetSummary('');

    void (async () => {
      const sid = Number(letter.senderUserId);
      let senderName: string | null = null;
      let loginFromUser: string | null = null;
      try {
        if (Number.isFinite(sid) && sid > 0) {
          const [loginRes, gr] = await Promise.allSettled([
            getLoginIdByUserId(sid),
            getGuardianByUserId(sid),
          ]);
          if (loginRes.status === 'fulfilled' && loginRes.value?.trim()) {
            loginFromUser = loginRes.value.trim();
          }
          if (gr.status === 'fulfilled' && gr.value?.name?.trim()) {
            senderName = gr.value.name.trim();
          }
          if (!senderName && loginFromUser) {
            const g2 = await getGuardianByLoginId(loginFromUser);
            if (g2?.name?.trim()) senderName = g2.name.trim();
          }
        }
        if (!cancelled) {
          setSenderLoginId(loginFromUser ?? letter.senderLoginId?.trim() ?? null);
          setSenderGuardianName(senderName ?? letter.senderGuardianName?.trim() ?? null);
        }
      } catch {
        if (!cancelled) {
          setSenderLoginId(letter.senderLoginId?.trim() ?? null);
          setSenderGuardianName(letter.senderGuardianName?.trim() ?? null);
        }
      }

      const tt = String(letter.targetType ?? '').toUpperCase();
      let summary = '';
      try {
        const letterKg =
          resolveLetterKindergartenId(letter as unknown as Record<string, unknown>) ??
          (Number.isFinite(Number(letter.kindergartenId)) && Number(letter.kindergartenId) > 0
            ? Math.trunc(Number(letter.kindergartenId))
            : null);
        if (tt === 'TEACHER') {
          const [trow, krow] = await Promise.all([
            getTeacher(letter.targetId),
            getKindergarten(letterKg ?? letter.kindergartenId),
          ]);
          // 공개 범위: 교사 ID는 숨김
          summary = `${trow.name} · ${krow.name}`;
        } else {
          const krow = await getKindergarten(letter.targetId);
          // 공개 범위: 유치원 ID도 함께 숨김(기본값은 이름만 표시)
          summary = `${krow.name}`;
        }
      } catch {
        // 조회 실패 시에도 ID를 노출하지 않음
        summary = `${targetTypeLabel(letter.targetType)}`;
      }
      if (!cancelled) setTargetSummary(summary);
      if (!cancelled) setMetaLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [letter]);

  useEffect(() => {
    const resetToTop = () => {
      window.scrollTo({ top: 0, behavior: 'auto' });
      document.documentElement.scrollTop = 0;
      document.body.scrollTop = 0;
      const container = document.getElementById('app-scroll-container');
      if (container) container.scrollTo({ top: 0, behavior: 'auto' });
    };
    resetToTop();
    const frame = window.requestAnimationFrame(resetToTop);
    return () => window.cancelAnimationFrame(frame);
  }, [id]);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      setResolvedLetterId(null);

      if (!Number.isFinite(id) || id <= 0) {
        setLetter(null);
        setError('유효하지 않은 감사 편지 ID입니다.');
        setLoading(false);
        return;
      }

      try {
        const detail = await getAppreciationLetterDetail(id);
        if (
          !viewerMaySeeAppreciationLetter(
            detail,
            buildAppreciationLetterViewerContext(user),
            isAuthenticated,
          )
        ) {
          setLetter(null);
          setError(
            isAuthenticated
              ? '이 감사 편지를 열람할 수 없습니다. 소속 유치원의 편지만 볼 수 있습니다.'
              : '로그인 후 소속 유치원의 감사 편지를 볼 수 있습니다.',
          );
        } else {
          setLetter(detail);
          const pk = resolveAppreciationLetterId(detail as unknown as Record<string, unknown>) ?? id;
          setResolvedLetterId(Number.isFinite(pk) && pk > 0 ? Math.trunc(pk) : null);
        }
      } catch (e) {
        console.warn('감사 편지 상세 조회 실패:', e);
        setError('감사 편지를 불러오지 못했습니다.');
        setLetter(null);
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [
    id,
    user,
    isAuthenticated,
  ]);

  const handleDeleteConfirm = async () => {
    if (!letter || !canEdit) return;
    setDeleteConfirmOpen(false);
    setDeleting(true);
    try {
      const deleteId = apiLetterId;
      if (deleteId != null) {
        await deleteAppreciationLetter(deleteId);
      } else {
        toast.error('삭제할 수 없습니다. 목록에서 다시 열어 주세요.');
        return;
      }
      toast.success('삭제되었습니다.');
      router.push(`/letters?reload=${Date.now()}`);
    } catch (e) {
      console.warn('감사 편지 삭제 실패:', e);
      toast.error(getApiErrorMessage(e, '삭제에 실패했습니다.'));
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="flex min-h-full flex-col bg-gray-50 px-4 py-4 sm:px-5 sm:py-5">
      <main className="mx-auto flex w-full max-w-[38.4rem] flex-1 flex-col">
        <div className="flex flex-1 flex-col rounded-2xl bg-white p-6 shadow-lg min-h-0">
          <div className="mb-5 flex items-center gap-2.5 border-b border-gray-200 pb-5">
            <Heart className="h-6 w-6 text-[#006b52]" />
            <h2 className="text-xl font-semibold tracking-tight">감사 편지</h2>
          </div>

          <div className="flex min-h-0 flex-1 flex-col">
            {loading && <p className="py-12 text-center text-sm text-gray-500">불러오는 중입니다.</p>}

            {!loading && error && <p className="rounded-lg bg-red-50 p-4 text-sm text-red-600">{error}</p>}

            {!loading && !error && letter && (
              <article className="space-y-5">
                <div className="flex gap-2.5 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-800">
                  <User className="mt-0.5 h-4 w-4 shrink-0 text-[#006b52]" />
                  <div>
                    <p className="font-medium text-slate-900">작성자</p>
                    <p className="mt-1 flex flex-wrap items-baseline gap-x-2 gap-y-1 text-slate-700">
                      <span>
                        <span className="text-slate-600">이름</span>{' '}
                        {metaLoading ? (
                          <span className="text-slate-500">불러오는 중…</span>
                        ) : senderGuardianName ? (
                          <span className="font-medium text-slate-900">{senderGuardianName}</span>
                        ) : (
                          <span className="text-slate-400">—</span>
                        )}
                      </span>
                      {resolvedAuthorLoginLabel && (
                        <span>
                          <span className="text-slate-600">로그인 ID</span>{' '}
                          <span className="font-medium text-slate-900">
                            {resolvedAuthorLoginLabel}
                          </span>
                        </span>
                      )}
                    </p>
                    {hydrated &&
                      user &&
                      isSameAppreciationLetterAuthor(user.id, letter.senderUserId) && (
                        <p className="mt-2 text-slate-600">본인이 작성한 글입니다.</p>
                      )}
                    {hydrated && user && !isSameAppreciationLetterAuthor(user.id, letter.senderUserId) && (
                      <p className="mt-2 text-xs text-slate-500">
                        수정·삭제는 작성자 본인 계정으로 로그인한 경우에만 가능합니다.
                      </p>
                    )}
                  </div>
                </div>

                <h1 className="text-xl font-semibold tracking-tight text-slate-900">{letter.title}</h1>
                <div className="flex flex-wrap gap-3 text-sm text-gray-600">
                  <span>등록 {formatLetterDateTime(letter.createdAt)}</span>
                  <span className="text-gray-300">|</span>
                  <span>
                    대상: {targetTypeLabel(letter.targetType)}
                    {targetSummary
                      ? ` — ${targetSummary}`
                      : metaLoading
                        ? ' — 이름 불러오는 중…'
                        : ''}
                  </span>
                  <span className="text-gray-300">|</span>
                  <span>{isAppreciationLetterPublic(letter) ? '공개' : '비공개'}</span>
                  <span className="text-gray-300">|</span>
                  <span>상태: {letterStatusLabel(letter.status)}</span>
                </div>
                <div className="whitespace-pre-wrap border-t border-gray-100 pt-5 text-sm leading-relaxed text-slate-800">
                  {letter.content}
                </div>
              </article>
            )}
          </div>

          <div className="mt-auto flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 pt-5">
            <Link
              href="/letters"
              className="inline-flex items-center gap-2 text-sm text-[#006b52] transition-colors hover:text-[#005640]"
            >
              <List className="h-4 w-4" />
              목록으로
            </Link>
            {hydrated && !loading && !error && letter && canEdit && (
              <div className="flex flex-wrap items-center gap-2">
                {apiLetterId != null && (
                  <Link
                    href={`/letters/edit?id=${apiLetterId}`}
                    className="inline-flex items-center gap-1 rounded-lg border border-gray-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-gray-50"
                  >
                    <Pencil className="h-4 w-4" />
                    수정
                  </Link>
                )}
                <button
                  type="button"
                  disabled={deleting}
                  onClick={() => setDeleteConfirmOpen(true)}
                  className="inline-flex items-center gap-1 rounded-lg border border-red-200 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50"
                >
                  <Trash2 className="h-4 w-4" />
                  {deleting ? '삭제 중…' : '삭제'}
                </button>
              </div>
            )}
          </div>
        </div>
      </main>

      {deleteConfirmOpen && letter && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4"
          role="presentation"
          onClick={() => !deleting && setDeleteConfirmOpen(false)}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="letter-delete-dialog-title"
            className="w-full max-w-[22.4rem] rounded-2xl bg-white p-5 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 id="letter-delete-dialog-title" className="text-lg font-semibold text-slate-900">
              삭제 확인
            </h3>
            <p className="mt-3 text-sm leading-relaxed text-slate-600">
              이 감사 편지를 삭제하시겠습니까? 삭제한 뒤에는 되돌릴 수 없습니다.
            </p>
            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                disabled={deleting}
                onClick={() => setDeleteConfirmOpen(false)}
                className="rounded-lg border border-gray-300 px-4 py-2 text-sm text-slate-700 transition-colors hover:bg-gray-50 disabled:opacity-50"
              >
                취소
              </button>
              <button
                type="button"
                disabled={deleting}
                onClick={() => void handleDeleteConfirm()}
                className="rounded-lg bg-red-600 px-4 py-2 text-sm text-white transition-colors hover:bg-red-700 disabled:opacity-50"
              >
                삭제
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
