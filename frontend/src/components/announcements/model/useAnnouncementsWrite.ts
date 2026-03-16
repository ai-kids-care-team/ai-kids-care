'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  createAnnouncement,
  getAnnouncementsMeta,
  type AnnouncementStatusOption,
  type CreateAnnouncementPayload,
} from '@/services/apis/announcements.api';

type StatusCode = 'ACTIVE' | 'PENDING' | 'DISABLED';

function toIsoOrNull(value: string) {
  if (!value) return null;
  return new Date(value).toISOString();
}

export function useAnnouncementsWrite() {
  const router = useRouter();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isPinned, setIsPinned] = useState(false);
  const [pinnedUntil, setPinnedUntil] = useState('');
  const [publishedAt, setPublishedAt] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [status, setStatus] = useState<StatusCode>('PENDING');
  const [statusOptions, setStatusOptions] = useState<AnnouncementStatusOption[]>([]);
  const [canWrite, setCanWrite] = useState(false);
  const [loadingMeta, setLoadingMeta] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const loadMeta = async () => {
      setLoadingMeta(true);
      setError('');
      try {
        const meta = await getAnnouncementsMeta();
        setCanWrite(meta.canWrite);
        setStatusOptions(meta.statusOptions);
        if (meta.statusOptions.length > 0) {
          setStatus(meta.statusOptions[0].code);
        }
      } catch (e) {
        console.error('공지사항 메타 정보 조회 실패:', e);
        setError('권한/코드 정보를 불러오지 못했습니다.');
      } finally {
        setLoadingMeta(false);
      }
    };

    void loadMeta();
  }, []);

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
    if (startsAt && endsAt && new Date(startsAt) > new Date(endsAt)) {
      setError('게시 종료일은 게시 시작일보다 빠를 수 없습니다.');
      return;
    }

    const payload: CreateAnnouncementPayload = {
      title: title.trim(),
      body: content.trim(),
      pinned: isPinned,
      pinnedUntil: toIsoOrNull(pinnedUntil),
      status,
      publishedAt: toIsoOrNull(publishedAt),
      startsAt: toIsoOrNull(startsAt),
      endsAt: toIsoOrNull(endsAt),
    };

    try {
      setSubmitting(true);
      await createAnnouncement(payload);
      router.push('/announcements');
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
    canWrite,
    loadingMeta,
    submitting,
    error,
    handleSubmit,
  };
}
