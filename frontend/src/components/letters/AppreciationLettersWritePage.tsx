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
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-3xl">
        <div className="mb-4">
          <Link
            href="/letters"
            className="inline-flex items-center gap-2 text-sm text-[#006b52] transition-colors hover:text-[#005640]"
          >
            <List className="h-4 w-4" />
            목록으로
          </Link>
        </div>

        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-8 flex items-center gap-3 border-b border-gray-200 pb-6">
            <Heart className="h-7 w-7 text-[#006b52]" />
            <h2 className="text-2xl font-semibold">감사 편지 작성</h2>
          </div>

          {!mounted ? (
            <div
              className="min-h-[420px] rounded-lg border border-slate-100 bg-slate-50/60 p-8 text-center text-sm text-slate-500"
              aria-busy="true"
            >
              화면을 불러오는 중…
            </div>
          ) : !isAuthenticated || !user || !token ? (
            <div className="rounded-lg border border-amber-100 bg-amber-50 p-6 text-center text-sm text-amber-900">
              <p className="mb-4">로그인한 뒤 작성할 수 있습니다.</p>
              <button
                type="button"
                onClick={() => openLoginModal()}
                className="rounded-lg bg-[#006b52] px-5 py-2 text-white hover:bg-[#005640]"
              >
                로그인
              </button>
            </div>
          ) : !canWriteAppreciationLetters(user.role) ? (
            <div className="rounded-lg border border-amber-100 bg-amber-50 p-6 text-center text-sm text-amber-900">
              <p className="mb-4">
                감사 편지는 <strong>보호자(학부모)</strong> 계정으로 로그인한 경우에만 작성할 수 있습니다.
              </p>
              <Link
                href="/letters"
                className="inline-block rounded-lg border border-amber-200 bg-white px-5 py-2 text-amber-900 hover:bg-amber-50"
              >
                목록으로
              </Link>
            </div>
          ) : (
            <form onSubmit={(ev) => void handleSubmit(ev)} className="space-y-5">
              <GuardianAuthorCard footnote="이 글을 작성한 본인 계정으로 로그인한 경우에만 이 글을 수정·삭제할 수 있습니다." />

              <LetterTargetPicker
                targetType={targetType}
                onTargetTypeChange={handleTargetTypeChange}
                onSelectKindergarten={handleSelectKindergarten}
                onSelectTeacher={handleSelectTeacher}
                selectedDisplayText={targetLabel}
                hasSelection={hasTarget}
                onClearTarget={handleClearLetterTarget}
              />

              <p className="text-xs text-gray-500">
                등록 시 상태는 <strong className="text-slate-700">게시(ACTIVE)</strong>로 저장됩니다.
              </p>

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
                  rows={10}
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

              <div className="flex justify-end gap-2 border-t border-gray-100 pt-6">
                <Link
                  href="/letters"
                  className="rounded-lg border border-gray-300 px-5 py-2.5 text-sm text-slate-700 hover:bg-gray-50"
                >
                  취소
                </Link>
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-lg bg-[#006b52] px-5 py-2.5 text-sm text-white hover:bg-[#005640] disabled:opacity-50"
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
