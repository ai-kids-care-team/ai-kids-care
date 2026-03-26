'use client';

import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  createAnnouncement,
  DEFAULT_ANNOUNCEMENT_STATUS_OPTIONS,
  validateAnnouncementCreateAuditFields,
  type AnnouncementWritePayload,
} from '@/services/apis/announcements.api';
import { useAppSelector } from '@/store/hook';
import { canManageAnnouncements } from '@/types/user-role';

import { StatusCode } from '@/types/announcement';

function toIsoOrNull(value: string) {
  if (!value) return null;
  return new Date(value).toISOString();
}

export function useAnnouncementsWrite() {
  const router = useRouter();
  const { user, token, isAuthenticated } = useAppSelector((state) => state.user);


    console.log(user , token, isAuthenticated);

  const authorIdHiddenValue = user?.id ?? '';
  const authorId = useMemo(() => {
    const parsed = Number(user?.id);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }, [user?.id]);
  const canWrite = useMemo(
    () => Boolean(isAuthenticated && user && token && canManageAnnouncements(user.role)),
    [isAuthenticated, user, token],
  );

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isPinned, setIsPinned] = useState(false);
  const [pinnedUntil, setPinnedUntil] = useState('');
  const [publishedAt, setPublishedAt] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [status, setStatus] = useState<StatusCode>('PENDING');
  const statusOptions = DEFAULT_ANNOUNCEMENT_STATUS_OPTIONS;
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const moveToAnnouncementsList = () => {
    if (typeof window !== 'undefined') {
      sessionStorage.removeItem('announcements:list:scrollY');
      window.scrollTo({ top: 0, behavior: 'auto' });
      document.documentElement.scrollTop = 0;
      document.body.scrollTop = 0;
      const container = document.getElementById('app-scroll-container');
      if (container) {
        container.scrollTo({ top: 0, behavior: 'auto' });
      }
    }
    router.push('/announcements', { scroll: true });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!canWrite) {
      setError('공지사항 작성 권한이 없습니다.');
      return;
    }
    if (!title.trim()) {
      setError('제목을 입력해주세요.');
      return;
    }
    if (!content.trim()) {
      setError('내용을 입력해주세요.');
      return;
    }
    if (authorId == null) {
      setError('로그인 사용자 ID를 확인할 수 없습니다.');
      return;
    }
    if (startsAt && endsAt && new Date(startsAt) > new Date(endsAt)) {
      setError('게시 종료일은 게시 시작일보다 빠를 수 없습니다.');
      return;
    }

    const nowIso = new Date().toISOString();
    const createdAt = nowIso;
    const updatedAt = nowIso;
    const auditMsg = validateAnnouncementCreateAuditFields(createdAt, updatedAt);
    if (auditMsg) {
      setError(auditMsg);
      return;
    }

    const payload: AnnouncementWritePayload = {
      title: title.trim(),
      body: content.trim(),
      isPinned,
      pinnedUntil: toIsoOrNull(pinnedUntil),
      status,
      publishedAt: toIsoOrNull(publishedAt),
      startsAt: toIsoOrNull(startsAt),
      endsAt: toIsoOrNull(endsAt),
      createdAt,
      updatedAt,
      authorId,
    };
    try {
      setSubmitting(true);
      await createAnnouncement(payload);
      moveToAnnouncementsList();
    } catch (e) {
      console.error('공지사항 등록 실패:', e);
      setError('공지사항 등록에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return {
    title,
    setTitle,
    content,
    setContent,
    isPinned,
    setIsPinned,
    pinnedUntil,
    setPinnedUntil,
    publishedAt,
    setPublishedAt,
    startsAt,
    setStartsAt,
    endsAt,
    setEndsAt,
    status,
    setStatus,
    statusOptions,
    authorIdHiddenValue,
    canWrite,
    submitting,
    error,
    handleSubmit,
    moveToAnnouncementsList,
  };
}
