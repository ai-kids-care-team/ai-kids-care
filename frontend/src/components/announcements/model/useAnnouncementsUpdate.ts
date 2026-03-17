'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  getAnnouncementDetail,
  getAnnouncementForEdit,
  getAnnouncementsMeta,
  type AnnouncementStatusOption,
  type CreateAnnouncementPayload,
  updateAnnouncement,
} from '@/services/apis/announcements.api';

type StatusCode = 'ACTIVE' | 'PENDING' | 'DISABLED';

function toIsoOrNull(value: string) {
  if (!value) return null;
  return new Date(value).toISOString();
}

function toLocalDatetimeInput(value: string | null) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  const hh = String(date.getHours()).padStart(2, '0');
  const mi = String(date.getMinutes()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}T${hh}:${mi}`;
}

export function useAnnouncementsUpdate() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const id = Number(searchParams.get('id') ?? 0);

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
  const [loadingAnnouncement, setLoadingAnnouncement] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const load = async () => {
      if (!Number.isFinite(id) || id <= 0) {
        setError('유효하지 않은 공지사항 ID입니다.');
        setLoadingMeta(false);
        setLoadingAnnouncement(false);
        return;
      }

      setLoadingMeta(true);
      setLoadingAnnouncement(true);
      setError('');

      try {
        const meta = await getAnnouncementsMeta();
        setCanWrite(meta.canWrite);
        setStatusOptions(meta.statusOptions);
      } catch (e) {
        console.error('공지사항 메타 정보 조회 실패:', e);
      } finally {
        setLoadingMeta(false);
      }

      try {
        const target = await getAnnouncementForEdit(id);
        // /edit 조회가 성공하면 수정 권한이 있는 상태다.
        setCanWrite(true);
        setTitle(target.title);
        setContent(target.body);
        setIsPinned(Boolean(target.pinned));
        setPinnedUntil(toLocalDatetimeInput(target.pinnedUntil));
        setPublishedAt(toLocalDatetimeInput(target.publishedAt));
        setStartsAt(toLocalDatetimeInput(target.startsAt));
        setEndsAt(toLocalDatetimeInput(target.endsAt));
        setStatus(target.status);
      } catch (e) {
        // 백엔드에 /edit API가 아직 반영되지 않았을 때를 대비해 상세 API로 기본값 채움
        try {
          const detail = await getAnnouncementDetail(id);
          setTitle(detail.title);
          setContent(detail.body);
          setPublishedAt(toLocalDatetimeInput(detail.publishedAt ?? detail.createdAt));
          setStartsAt('');
          setEndsAt('');
          setPinnedUntil('');
          setIsPinned(false);
        } catch (detailError) {
          console.error('공지사항 수정 정보 조회 실패:', e, detailError);
          setError('공지사항 수정 정보를 불러오지 못했습니다.');
        }
      } finally {
        setLoadingAnnouncement(false);
      }
    };

    void load();
  }, [id]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!canWrite) {
      setError('공지사항 수정 권한이 없습니다.');
      return;
    }
    if (!Number.isFinite(id) || id <= 0) {
      setError('유효하지 않은 공지사항 ID입니다.');
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
      await updateAnnouncement(id, payload);
      router.push(`/announcements/read?id=${id}`, { scroll: true });
    } catch (e) {
      console.error('공지사항 수정 실패:', e);
      setError('공지사항 수정에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return {
    id,
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
    loadingAnnouncement,
    submitting,
    error,
    handleSubmit,
  };
}
