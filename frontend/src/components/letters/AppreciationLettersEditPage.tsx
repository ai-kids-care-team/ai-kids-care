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
import type { AppreciationLetterVO } from '@/types/appreciationLetter';
import { useAppSelector } from '@/store/hook';
import { openLoginModal } from '@/utils/auth-modal';
import { GuardianAuthorCard } from './GuardianAuthorCard';
import { getApiErrorMessage } from './api-error-message';
import {
  parseLetterIdQueryParam,
  targetTypeLabel,
} from './appreciation-letter-utils';
import { canWriteAppreciationLetters } from '@/types/user-role';

export function AppreciationLettersEditPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const id = parseLetterIdQueryParam(searchParams.get('id')) ?? NaN;
  const { user, isAuthenticated } = useAppSelector((state) => state.user);

  const [loading, setLoading] = useState(true);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isPublic, setIsPublic] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [loadedLetter, setLoadedLetter] = useState<AppreciationLetterVO | null>(null);

  const canEdit = useMemo(
    () => Boolean(isAuthenticated && user),
    [isAuthenticated, user],
  );

  useEffect(() => {
    if (!user?.id) {
      setLoadedLetter(null);
      setLoading(false);
      setLoadError('');
      return;
    }

    if (!canWriteAppreciationLetters(user.role)) {
      setLoading(false);
      setLoadError('감사 편지는 보호자(학부모) 계정만 수정할 수 있습니다.');
      setLoadedLetter(null);
      return;
    }

    if (!Number.isFinite(id) || id <= 0) {
      setLoadError('유효하지 않은 ID입니다.');
      setLoading(false);
      setLoadedLetter(null);
      return;
    }

    const load = async () => {
      setLoading(true);
      setLoadError('');
      setLoadedLetter(null);
      try {
        const row = await getAppreciationLetterDetail(id);

        if (!row.editable) {
          setLoadError('수정 권한이 없습니다.');
          setLoadedLetter(null);
          return;
        }

        setLoadedLetter(row);
        setTitle(row.title);
        setContent(row.content);
        setIsPublic(row.isPublic !== false);
      } catch (e) {
        console.warn('감사 편지 불러오기 실패:', e);
        setLoadError(getApiErrorMessage(e, '감사 편지를 불러오지 못했습니다.'));
        setLoadedLetter(null);
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [id, user, isAuthenticated]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canEdit || loadError || !loadedLetter) return;

    if (!title.trim() || !content.trim()) {
      toast.error('제목과 내용을 입력해주세요.');
      return;
    }

    const putId = loadedLetter.letterId;
    if (!Number.isFinite(putId) || putId <= 0) {
      toast.error('저장할 편지 ID를 찾을 수 없습니다. 목록에서 다시 열어 주세요.');
      return;
    }

    setSubmitting(true);
    try {
      await updateAppreciationLetter(putId, {
        title: title.trim(),
        content: content.trim(),
        isPublic,
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
              Number.isFinite(id) && id > 0
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

          {!loading && !loadError && canEdit && user && loadedLetter && (
            <form onSubmit={(ev) => void handleSubmit(ev)} className="space-y-5">
              <GuardianAuthorCard heading="작성자 (수정 불가)" />

              <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-700">
                <p className="text-xs font-medium text-slate-500 mb-1">감사 대상 (수정 불가)</p>
                <p>
                  {targetTypeLabel(loadedLetter.targetType)}
                  {loadedLetter.targetName ? ` — ${loadedLetter.targetName}` : ''}
                </p>
              </div>

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
                  href={`/letters/read?id=${id}`}
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
