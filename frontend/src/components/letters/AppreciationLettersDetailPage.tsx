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
  getClientCachedLetterBySeq,
  listClientCachedLetters,
  removeClientCachedLetter,
  parseClientLetterSeqParam,
} from './appreciation-letter-client-cache';
import {
  formatLetterDateTime,
  isAppreciationLetterPublic,
  isSameAppreciationLetterAuthor,
  letterStatusLabel,
  parseLetterIdQueryParam,
  targetTypeLabel,
  viewerMaySeeAppreciationLetter,
} from './appreciation-letter-utils';
import { getGuardianByLoginId, getGuardianByUserId } from '@/services/apis/guardians.api';
import { getLoginIdByUserId } from '@/services/apis/usersPublic.api';
import { getKindergarten } from '@/services/apis/kindergartens.api';
import { getTeacher } from '@/services/apis/teachers.api';
import { useAppSelector } from '@/store/hook';
import { canWriteAppreciationLetters } from '@/types/user-role';

export function AppreciationLettersDetailPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user, token, isAuthenticated } = useAppSelector((state) => state.user);
  const clientSeq = parseClientLetterSeqParam(searchParams.get('cid'));
  const id = parseLetterIdQueryParam(searchParams.get('id')) ?? NaN;
  const sig = searchParams.get('sig')?.trim() ?? '';
  const isSigView = sig !== '';
  const isClientView = clientSeq != null;
  const [hydrated, setHydrated] = useState(false);
  const [letter, setLetter] = useState<AppreciationLetterVO | null>(null);
  const [resolvedClientSeq, setResolvedClientSeq] = useState<number | null>(null);
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

  const canEdit = useMemo(
    () =>
      Boolean(
        isAuthenticated &&
          token &&
          letter &&
          user &&
          (resolvedClientSeq != null || resolvedLetterId != null || (Number.isFinite(id) && id > 0)) &&
          canWriteAppreciationLetters(user.role) &&
          isSameAppreciationLetterAuthor(user.id, letter.senderUserId),
      ),
    [isAuthenticated, token, letter, user, resolvedClientSeq, resolvedLetterId, id],
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
        if (tt === 'TEACHER') {
          const [trow, krow] = await Promise.all([
            getTeacher(letter.targetId),
            getKindergarten(letter.kindergartenId),
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
      setResolvedClientSeq(null);
      setResolvedLetterId(null);

      if (isClientView) {
        const cached = getClientCachedLetterBySeq(clientSeq!);
        if (!cached) {
          setLetter(null);
          setError('작성 직후 캐시 글을 불러오지 못했습니다. 목록에서 다시 열어 주세요.');
          setLoading(false);
          return;
        }
        if (!viewerMaySeeAppreciationLetter(cached, user?.id, isAuthenticated)) {
          setLetter(null);
          setError('비공개 글입니다. 작성자 본인만 볼 수 있습니다.');
          setLoading(false);
          return;
        }
        setLetter(cached);
        setResolvedClientSeq(clientSeq!);
        setLoading(false);
        return;
      }

      if (isSigView) {
        type SigPayload = {
          title: string;
          senderUserId: number;
          targetType: string;
          targetId: number;
        };

        const parseSigPayload = (raw: string): SigPayload | null => {
          try {
            const parsed = JSON.parse(raw) as {
              title?: unknown;
              senderUserId?: unknown;
              targetType?: unknown;
              targetId?: unknown;
            };
            const title = typeof parsed?.title === 'string' ? parsed.title : null;
            const senderUserId = Number(parsed?.senderUserId);
            const targetType =
              typeof parsed?.targetType === 'string' ? parsed.targetType : null;
            const targetId = Number(parsed?.targetId);

            if (title && Number.isFinite(senderUserId) && targetType && Number.isFinite(targetId)) {
              return {
                title,
                senderUserId,
                targetType: String(targetType).toUpperCase(),
                targetId,
              };
            }
          } catch {
            // ignore
          }
          return null;
        };

        const payload = parseSigPayload(sig);
        if (!payload) {
          setLetter(null);
          setError('잘못된 시그니처입니다.');
          setLoading(false);
          return;
        }

        // 1) 먼저 client cache에서 찾기 (작성 직후 빠른 열람)
        const candidates = listClientCachedLetters();
        const found = candidates.find(({ vo }) => {
          return (
            vo.title === payload.title &&
            vo.senderUserId === payload.senderUserId &&
            String(vo.targetType ?? '').toUpperCase() === payload.targetType &&
            vo.targetId === payload.targetId
          );
        });

        if (found) {
          if (!viewerMaySeeAppreciationLetter(found.vo, user?.id, isAuthenticated)) {
            setLetter(null);
            setError('비공개 글입니다. 작성자 본인만 볼 수 있습니다.');
            setResolvedClientSeq(null);
            setResolvedLetterId(null);
            setLoading(false);
            return;
          }
          setLetter(found.vo);
          setResolvedClientSeq(found.seq);
          setLoading(false);
          return;
        }

        // 2) cache에 없으면 백엔드(DB)에서 실제 id를 찾아서 상세 표시
        const MAX_RESOLVE_ID = 200;
        for (let did = 1; did <= MAX_RESOLVE_ID; did++) {
          let detail: AppreciationLetterVO | null = null;
          try {
            detail = await getAppreciationLetterDetail(did);
          } catch {
            // 해당 id가 없을 수 있음
          }
          if (!detail) continue;

          const isMatch =
            detail.title === payload.title &&
            detail.senderUserId === payload.senderUserId &&
            String(detail.targetType ?? '').toUpperCase() === payload.targetType &&
            detail.targetId === payload.targetId;

          if (!isMatch) continue;

          if (!viewerMaySeeAppreciationLetter(detail, user?.id, isAuthenticated)) {
            setLetter(null);
            setError('비공개 글입니다. 작성자 본인만 볼 수 있습니다.');
            setResolvedClientSeq(null);
            setResolvedLetterId(null);
            setLoading(false);
            return;
          }

          setLetter(detail);
          setResolvedLetterId(did);
          setLoading(false);
          return;
        }

        setLetter(null);
        setError('해당 글을 찾지 못했습니다. 목록에서 다시 열어 주세요.');
        setLoading(false);
        return;
      }

      if (!Number.isFinite(id) || id <= 0) {
        setLetter(null);
        setError('유효하지 않은 감사 편지 ID입니다.');
        setLoading(false);
        return;
      }

      try {
        const detail = await getAppreciationLetterDetail(id);
        if (!viewerMaySeeAppreciationLetter(detail, user?.id, isAuthenticated)) {
          setLetter(null);
          setError('비공개 글입니다. 작성자 본인만 볼 수 있습니다.');
        } else {
          setLetter(detail);
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
  }, [id, clientSeq, isClientView, sig, isSigView, user?.id, isAuthenticated]);

  const handleDeleteConfirm = async () => {
    if (!letter || !canEdit) return;
    setDeleteConfirmOpen(false);
    setDeleting(true);
    try {
      if (resolvedClientSeq != null) {
        removeClientCachedLetter(resolvedClientSeq);
        toast.success('삭제되었습니다.');
        router.push('/letters');
        return;
      }

      if (resolvedLetterId != null) {
        await deleteAppreciationLetter(resolvedLetterId);
      } else if (Number.isFinite(id) && id > 0) {
        await deleteAppreciationLetter(id);
      } else {
        toast.error('삭제할 수 없습니다. 목록에서 다시 열어 주세요.');
        return;
      }
      toast.success('삭제되었습니다.');
      router.push('/letters');
    } catch (e) {
      console.warn('감사 편지 삭제 실패:', e);
      toast.error('삭제에 실패했습니다.');
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-3xl">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <Link
            href="/letters"
            className="inline-flex items-center gap-2 text-sm text-[#006b52] transition-colors hover:text-[#005640]"
          >
            <List className="h-4 w-4" />
            목록으로
          </Link>
          {hydrated && !loading && !error && letter && canEdit && (
            <div className="flex items-center gap-2">
              {(resolvedClientSeq != null ||
                (resolvedLetterId != null && Number.isFinite(resolvedLetterId)) ||
                (Number.isFinite(id) && id > 0)) && (
                <Link
                  href={
                    resolvedClientSeq != null
                      ? `/letters/edit?cid=${resolvedClientSeq}`
                      : resolvedLetterId != null
                        ? `/letters/edit?id=${resolvedLetterId}`
                        : `/letters/edit?id=${id}`
                  }
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

        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-6 flex items-center gap-3 border-b border-gray-200 pb-6">
            <Heart className="h-7 w-7 text-[#006b52]" />
            <h2 className="text-2xl font-semibold">감사 편지</h2>
          </div>

          {loading && <p className="py-16 text-center text-gray-500">불러오는 중입니다.</p>}

          {!loading && error && <p className="rounded-lg bg-red-50 p-4 text-sm text-red-600">{error}</p>}

          {!loading && !error && letter && (
            <article className="space-y-6">
              <div className="flex gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-800">
                <User className="mt-0.5 h-5 w-5 shrink-0 text-[#006b52]" />
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

              <h1 className="text-2xl font-semibold text-slate-900">{letter.title}</h1>
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
              <div className="whitespace-pre-wrap border-t border-gray-100 pt-6 leading-relaxed text-slate-800">
                {letter.content}
              </div>
            </article>
          )}
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
            className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl"
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
