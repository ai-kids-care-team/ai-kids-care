'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Bell, List, Pencil, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { deleteAnnouncement, getAnnouncementDetail, type AnnouncementDetail } from '@/services/apis/announcements.api';
import { useAppSelector } from '@/store/hook';
import { canManageAnnouncements } from '@/types/user-role';

function formatDate(value: string | null) {
  if (!value) return '-';
  const date = new Date(value);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd}`;
}

export function AnnouncementsDetailPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user, token, isAuthenticated } = useAppSelector((state) => state.user);
  const id = Number(searchParams.get('id') ?? 0);
  const [announcement, setAnnouncement] = useState<AnnouncementDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');
  const authorIdHiddenValue = user?.id ?? '';

  useEffect(() => {
    const resetToTop = () => {
      window.scrollTo({ top: 0, behavior: 'auto' });
      document.documentElement.scrollTop = 0;
      document.body.scrollTop = 0;
      const container = document.getElementById('app-scroll-container');
      if (container) {
        container.scrollTo({ top: 0, behavior: 'auto' });
      }
    };

    resetToTop();
    const frame = window.requestAnimationFrame(resetToTop);
    return () => window.cancelAnimationFrame(frame);
  }, [id]);

  const canWrite = useMemo(
    () => Boolean(isAuthenticated && user && token && canManageAnnouncements(user.role)),
    [isAuthenticated, user, token],
  );

  useEffect(() => {
    if (!Number.isFinite(id) || id <= 0) {
      setAnnouncement(null);
      setError('유효하지 않은 공지사항 ID입니다.');
      setLoading(false);
      return;
    }

    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const detail = await getAnnouncementDetail(id);
        setAnnouncement(detail);
      } catch (e) {
        console.error('공지사항 상세 조회 실패:', e);
        setError('공지사항 상세 정보를 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [id]);

  const handleDelete = async () => {
    if (!Number.isFinite(id) || id <= 0) {
      setError('유효하지 않은 공지사항 ID입니다.');
      return;
    }

    const confirmed = window.confirm('정말 이 공지사항을 삭제하시겠습니까?');
    if (!confirmed) return;

    try {
      setDeleting(true);
      await deleteAnnouncement(id);
      router.push('/announcements?deleted=1');
    } catch (e) {
      console.error('공지사항 삭제 실패:', e);
      setError('공지사항 삭제에 실패했습니다.');
      toast.error('공지사항 삭제에 실패했습니다.');
    } finally {
      setDeleting(false);
    }
  };

  const handleDeleteSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const authorIdValue = String(formData.get('authorId') ?? '').trim();
    const parsedAuthorId = Number(authorIdValue);
    if (!Number.isFinite(parsedAuthorId) || parsedAuthorId <= 0) {
      setError('로그인 사용자 ID를 확인할 수 없습니다.');
      return;
    }
    await handleDelete();
  };

  if (loading) {
    return <div className="min-h-screen bg-gray-50 p-6 text-center text-gray-500">불러오는 중입니다.</div>;
  }

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-6xl">
        <div className="rounded-2xl bg-white p-8 shadow-lg">
          {error && <p className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-600">{error}</p>}
          {!error && announcement && (
            <>
              <div className="mb-8 border-b border-gray-200 pb-6">
                <div className="flex items-center gap-3">
                  <Bell className="h-7 w-7 text-[#006b52]" />
                  <h2 className="text-3xl text-slate-900">공지사항</h2>
                </div>
                <p className="mt-2 text-sm text-slate-500">공지사항 상세 내용을 확인하세요.</p>
              </div>

              <div className="rounded-2xl border border-emerald-100 bg-emerald-50/40 px-6 py-5">
                <h3 className="text-3xl leading-tight text-slate-900">{announcement.title}</h3>
                <p className="mt-4 text-sm text-slate-600">
                  작성일 {formatDate(announcement.publishedAt ?? announcement.createdAt)} 조회수 {announcement.viewCount}
                </p>
              </div>

              <hr className="my-8 border-slate-200" />

              <div className="whitespace-pre-line text-base leading-relaxed text-slate-800">{announcement.body}</div>

              <hr className="my-10 border-slate-200" />

              <div className="flex items-center justify-between">
                <Link
                  href="/announcements"
                  className="inline-flex items-center gap-2 rounded-xl bg-slate-100 px-6 py-3 text-sm text-slate-700 hover:bg-slate-200"
                >
                  <List className="h-5 w-5" />
                  목록으로
                </Link>

                {canWrite && (
                  <div className="flex items-center gap-3">
                    <Link
                      href={`/announcements/edit?id=${announcement.id}`}
                      className="inline-flex items-center gap-2 rounded-xl bg-blue-600 px-6 py-3 text-sm text-white hover:bg-blue-700"
                    >
                      <Pencil className="h-5 w-5" />
                      수정
                    </Link>
                    <form onSubmit={handleDeleteSubmit}>
                      <input type="hidden" name="authorId" value={authorIdHiddenValue} readOnly />
                      <button
                        type="submit"
                        disabled={deleting}
                        className="inline-flex items-center gap-2 rounded-xl bg-red-600 px-6 py-3 text-sm text-white hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-red-300"
                      >
                        <Trash2 className="h-5 w-5" />
                        {deleting ? '삭제 중...' : '삭제'}
                      </button>
                    </form>
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </main>
    </div>
  );
}
