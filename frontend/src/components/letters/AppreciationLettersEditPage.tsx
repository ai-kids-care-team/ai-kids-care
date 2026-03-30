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
import type { AppreciationTargetType } from '@/types/appreciationLetter';
import { useAppSelector } from '@/store/hook';
import { index as appStore } from '@/store/index';
import { openLoginModal } from '@/utils/auth-modal';
import { GuardianAuthorCard } from './GuardianAuthorCard';
import { LetterTargetPicker } from './LetterTargetPicker';
import { getApiErrorMessage } from '@/lib/api-error-message';
import { isSameAppreciationLetterAuthor, parseLetterIdQueryParam } from '@/lib/appreciation-letter-utils';
import { canWriteAppreciationLetters } from '@/types/user-role';

export function AppreciationLettersEditPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
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
  const [isPublic, setIsPublic] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [loadError, setLoadError] = useState('');
  /** DB에 저장된 작성자 — 수정 요청 시 그대로 전송(FK·일치 보장) */
  const [storedSenderUserId, setStoredSenderUserId] = useState<number | null>(null);
  const [presetKgForTeacherPicker, setPresetKgForTeacherPicker] = useState<{
    kindergartenId: number;
    name: string;
  } | null>(null);

  const canEdit = useMemo(
    () => Boolean(isAuthenticated && user && token && Number.isFinite(senderNum)),
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
    setTargetType('TEACHER');
    setKindergartenId(n.kindergartenId);
    setTargetId(n.teacherId);
    setTargetLabel(`${n.name} (교사)`);
  };

  useEffect(() => {
    if (!Number.isFinite(id) || id <= 0) {
      setLoadError('유효하지 않은 ID입니다.');
      setLoading(false);
      return;
    }

    /* Redux·localStorage 복원 전에 effect가 먼저 돌면 user가 비어 권한 오류가 난다 → user.id가 있을 때만 검증·폼 채움 */
    const currentUser = user;
    if (!currentUser?.id) {
      setLoading(false);
      setLoadError('');
      setStoredSenderUserId(null);
      return;
    }

    if (!canWriteAppreciationLetters(currentUser.role)) {
      setLoading(false);
      setLoadError('감사 편지는 보호자(학부모) 계정만 수정할 수 있습니다.');
      setStoredSenderUserId(null);
      return;
    }

    const load = async () => {
      setLoading(true);
      setLoadError('');
      try {
        const row = await getAppreciationLetterDetail(id);
        const me = appStore.getState().user.user;
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
        setIsPublic(row.isPublic);

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
  }, [id, user?.id, user?.role]);

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
      await updateAppreciationLetter(id, {
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
      router.push(`/letters/read?id=${id}`);
    } catch (err) {
      console.warn('감사 편지 수정 실패:', err);
      toast.error(getApiErrorMessage(err, '수정에 실패했습니다.'));
    } finally {
      setSubmitting(false);
    }
  };

  if (!canEdit && !loading) {
    return (
      <div className="min-h-screen bg-gray-50 p-6">
        <main className="mx-auto max-w-3xl">
          <div className="rounded-2xl bg-white p-8 shadow-lg text-center">
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
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-3xl">
        <div className="mb-4">
          <Link
            href={Number.isFinite(id) && id > 0 ? `/letters/read?id=${id}` : '/letters'}
            className="inline-flex items-center gap-2 text-sm text-[#006b52] transition-colors hover:text-[#005640]"
          >
            <List className="h-4 w-4" />
            상세로
          </Link>
        </div>

        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-8 flex items-center gap-3 border-b border-gray-200 pb-6">
            <Heart className="h-7 w-7 text-[#006b52]" />
            <h2 className="text-2xl font-semibold">감사 편지 수정</h2>
          </div>

          {loading && <p className="py-12 text-center text-gray-500">불러오는 중입니다.</p>}

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
                presetKindergartenForTeacherFlow={presetKgForTeacherPicker}
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
                  rows={10}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  required
                />
              </div>

              <label className="flex items-center gap-2 text-sm text-slate-700">
                <input type="checkbox" checked={isPublic} onChange={(e) => setIsPublic(e.target.checked)} />
                공개
              </label>

              <div className="flex justify-end gap-2 border-t border-gray-100 pt-6">
                <Link
                  href={`/letters/read?id=${id}`}
                  className="rounded-lg border border-gray-300 px-5 py-2.5 text-sm text-slate-700 hover:bg-gray-50"
                >
                  취소
                </Link>
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-lg bg-[#006b52] px-5 py-2.5 text-sm text-white hover:bg-[#005640] disabled:opacity-50"
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
