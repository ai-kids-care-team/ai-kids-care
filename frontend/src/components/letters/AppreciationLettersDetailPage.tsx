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
  formatLetterDateTime,
  parseLetterIdQueryParam,
  targetTypeLabel,
} from './appreciation-letter-utils';
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
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [error, setError] = useState('');

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
          letter &&
          user &&
          canWriteAppreciationLetters(user.role) &&
          letter.editable,
      ),
    [isAuthenticated, letter, user],
  );

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

      if (!Number.isFinite(id) || id <= 0) {
        setLetter(null);
        setError('유효하지 않은 감사 편지 ID입니다.');
        setLoading(false);
        return;
      }

      try {
        const detail = await getAppreciationLetterDetail(id);
        setLetter(detail);
      } catch (e) {
        console.warn('감사 편지 상세 조회 실패:', e);
        setError(getApiErrorMessage(e, '감사 편지를 불러오지 못했습니다.'));
        setLetter(null);
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [id]);

  const handleDeleteConfirm = async () => {
    if (!letter || !canEdit) return;
    setDeleteConfirmOpen(false);
    setDeleting(true);
    try {
      await deleteAppreciationLetter(letter.letterId);
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
                    <p className="mt-1 text-slate-700">
                      {letter.senderName ?? '—'}
                    </p>
                    {hydrated && letter.editable && (
                      <p className="mt-2 text-slate-600">내가 작성한 편지입니다.</p>
                    )}
                  </div>
                </div>

                <h1 className="text-xl font-semibold tracking-tight text-slate-900">{letter.title}</h1>
                <div className="flex flex-wrap gap-3 text-sm text-gray-600">
                  <span>등록 {formatLetterDateTime(letter.createdAt)}</span>
                  <span className="text-gray-300">|</span>
                  <span>
                    대상: {targetTypeLabel(letter.targetType)}
                    {letter.targetName ? ` — ${letter.targetName}` : ''}
                  </span>
                  <span className="text-gray-300">|</span>
                  <span>{letter.isPublic ? '공개' : '비공개'}</span>
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
                <Link
                  href={`/letters/edit?id=${letter.letterId}`}
                  className="inline-flex items-center gap-1 rounded-lg border border-gray-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-gray-50"
                >
                  <Pencil className="h-4 w-4" />
                  수정
                </Link>
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
