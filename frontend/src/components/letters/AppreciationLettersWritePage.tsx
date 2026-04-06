'use client';

import type { FormEvent } from 'react';
import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Heart, List } from 'lucide-react';
import { toast } from 'sonner';
import { createAppreciationLetter } from '@/services/apis/appreciationLetters.api';
import type { KindergartenVO } from '@/services/apis/kindergartens.api';
import { normalizeTeacherVO, type TeacherApiRow, type TeacherVO } from '@/services/apis/teachers.api';
import type { AppreciationTargetType } from '@/types/appreciationLetter';
import { useAppSelector } from '@/store/hook';
import { canWriteAppreciationLetters } from '@/types/user-role';
import { resolveViewerSessionKindergartenId } from '@/utils/session-kindergarten';
import { openLoginModal } from '@/utils/auth-modal';
import { GuardianAuthorCard } from './GuardianAuthorCard';
import { LetterTargetPicker } from './LetterTargetPicker';
import { getApiErrorMessage } from './api-error-message';
import { pushClientAppreciationLetter } from './appreciation-letter-client-cache';

export function AppreciationLettersWritePage() {
  const router = useRouter();
  const { user, token, isAuthenticated } = useAppSelector((state) => state.user);
  const senderNum = user?.id != null ? Number(user.id) : NaN;

  const [targetType, setTargetType] = useState<AppreciationTargetType>('KINDERGARTEN');
  const [kindergartenId, setKindergartenId] = useState<number | null>(null);
  const [targetId, setTargetId] = useState<number | null>(null);
  const [targetLabel, setTargetLabel] = useState('');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isPublic, setIsPublic] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  const canSubmit = useMemo(
    () =>
      Boolean(
        isAuthenticated &&
          user &&
          token &&
          Number.isFinite(senderNum) &&
          canWriteAppreciationLetters(user.role),
      ),
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
    const tid = n.teacherId;
    const kid = n.kindergartenId;
    if (tid <= 0 || kid <= 0) {
      toast.error('교사 정보가 올바르지 않습니다. 새로고침 후 다시 선택해 주세요.');
      return;
    }
    setTargetType('TEACHER');
    setKindergartenId(kid);
    setTargetId(tid);
    setTargetLabel(`${n.name} (교사)`);
  };

  const handleClearLetterTarget = () => {
    setKindergartenId(null);
    setTargetId(null);
    setTargetLabel('');
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;

    if (!hasTarget || kindergartenId == null || targetId == null) {
      toast.error('감사 대상(유치원 또는 교사)을 검색 후 목록에서 선택해 주세요.');
      return;
    }
    if (!title.trim() || !content.trim()) {
      toast.error('제목과 내용을 입력해주세요.');
      return;
    }

    setSubmitting(true);
    try {
      const created = await createAppreciationLetter({
        kindergartenId,
        senderUserId: senderNum,
        targetType,
        targetId,
        title: title.trim(),
        content: content.trim(),
        isPublic,
        status: 'ACTIVE',
      });
      // 목록 API에서 PK 매핑이 누락될 수 있어, 방금 작성한 글을 캐시에 즉시 반영합니다.
      pushClientAppreciationLetter({
        letterId: created.letterId,
        kindergartenId,
        senderUserId: senderNum,
        targetType,
        targetId,
        title: title.trim(),
        content: content.trim(),
        isPublic,
        status: 'ACTIVE',
        senderLoginId: (user?.loginId || user?.username || '').trim() || undefined,
      });
      toast.success('등록되었습니다.');
      router.push('/letters');
    } catch (err) {
      console.warn('감사 편지 등록 실패:', err);
      toast.error(getApiErrorMessage(err, '등록에 실패했습니다.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 px-3 py-3 sm:px-4 sm:py-4">
      <main className="mx-auto max-w-[30.72rem]">
        <div className="mb-3">
          <Link
            href="/letters"
            className="inline-flex items-center gap-1.5 text-xs text-[#006b52] transition-colors hover:text-[#005640] sm:text-sm"
          >
            <List className="h-3.5 w-3.5 sm:h-4 sm:w-4" />
            목록으로
          </Link>
        </div>

        <div className="rounded-xl bg-white p-4 shadow-lg sm:p-5">
          <div className="mb-4 flex items-center gap-2 border-b border-gray-200 pb-4">
            <Heart className="h-5 w-5 text-[#006b52]" />
            <h2 className="text-lg font-semibold tracking-tight sm:text-xl">감사 편지 작성</h2>
          </div>

          {!mounted ? (
            <div
              className="min-h-[270px] rounded-md border border-slate-100 bg-slate-50/60 p-4 text-center text-xs text-slate-500 sm:text-sm"
              aria-busy="true"
            >
              화면을 불러오는 중…
            </div>
          ) : !isAuthenticated || !user || !token ? (
            <div className="rounded-md border border-amber-100 bg-amber-50 p-4 text-center text-xs text-amber-900 sm:text-sm">
              <p className="mb-3">로그인한 뒤 작성할 수 있습니다.</p>
              <button
                type="button"
                onClick={() => openLoginModal()}
                className="rounded-md bg-[#006b52] px-4 py-1.5 text-sm text-white hover:bg-[#005640]"
              >
                로그인
              </button>
            </div>
          ) : !canWriteAppreciationLetters(user.role) ? (
            <div className="rounded-md border border-amber-100 bg-amber-50 p-4 text-center text-xs text-amber-900 sm:text-sm">
              <p className="mb-3">
                감사 편지는 <strong>보호자(학부모)</strong> 계정으로 로그인한 경우에만 작성할 수 있습니다.
              </p>
              <Link
                href="/letters"
                className="inline-block rounded-md border border-amber-200 bg-white px-4 py-1.5 text-sm text-amber-900 hover:bg-amber-50"
              >
                목록으로
              </Link>
            </div>
          ) : (
            <form onSubmit={(ev) => void handleSubmit(ev)} className="space-y-4">
              <GuardianAuthorCard
                compact
                footnote="이 글을 작성한 본인 계정으로 로그인한 경우에만 이 글을 수정·삭제할 수 있습니다."
              />

              <LetterTargetPicker
                compact
                targetType={targetType}
                onTargetTypeChange={handleTargetTypeChange}
                onSelectKindergarten={handleSelectKindergarten}
                onSelectTeacher={handleSelectTeacher}
                selectedDisplayText={targetLabel}
                hasSelection={hasTarget}
                onClearTarget={handleClearLetterTarget}
                lockedKindergartenId={guardianLockKg}
              />

              <p className="text-[11px] text-gray-500 sm:text-xs">
                등록 시 상태는 <strong className="text-slate-700">게시(ACTIVE)</strong>로 저장됩니다.
              </p>

              <div>
                <label className="mb-0.5 block text-xs font-medium text-slate-700 sm:text-sm">제목</label>
                <input
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-2.5 py-1.5 text-xs sm:rounded-lg sm:px-3 sm:py-2 sm:text-sm"
                  required
                />
              </div>

              <div>
                <label className="mb-0.5 block text-xs font-medium text-slate-700 sm:text-sm">내용</label>
                <textarea
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  rows={6}
                  className="w-full rounded-md border border-gray-300 px-2.5 py-1.5 text-xs sm:rounded-lg sm:px-3 sm:py-2 sm:text-sm"
                  required
                />
              </div>

              <label className="flex cursor-pointer items-center gap-1.5 text-xs text-slate-700 sm:text-sm">
                <input
                  type="checkbox"
                  checked={!isPublic}
                  onChange={(e) => setIsPublic(!e.target.checked)}
                />
                비공개 (나만 보기)
              </label>
              <p className="text-[11px] text-slate-500 sm:text-xs">
                체크하면 목록·상세에서 로그인한 작성자 본인에게만 보입니다.
              </p>

              <div className="flex justify-end gap-1.5 border-t border-gray-100 pt-4">
                <Link
                  href="/letters"
                  className="rounded-md border border-gray-300 px-3 py-1.5 text-xs text-slate-700 hover:bg-gray-50 sm:rounded-lg sm:px-4 sm:py-2 sm:text-sm"
                >
                  취소
                </Link>
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-md bg-[#006b52] px-3 py-1.5 text-xs text-white hover:bg-[#005640] disabled:opacity-50 sm:rounded-lg sm:px-4 sm:py-2 sm:text-sm"
                >
                  {submitting ? '등록 중…' : '등록'}
                </button>
              </div>
            </form>
          )}
        </div>
      </main>
    </div>
  );
}
