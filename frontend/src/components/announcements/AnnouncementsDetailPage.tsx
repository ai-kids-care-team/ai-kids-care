'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Pencil, Trash2, List } from 'lucide-react';
import { getAnnouncementDetail, getAnnouncementsMeta, type AnnouncementDetail } from '@/services/apis/announcements.api';

function formatDate(value: string | null) {
  if (!value) return '-';
  const date = new Date(value);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd}`;
}

export function AnnouncementsDetailPage() {
  const searchParams = useSearchParams();
  const id = Number(searchParams.get('id') ?? 0);
  const [announcement, setAnnouncement] = useState<AnnouncementDetail | null>(null);
  const [canWrite, setCanWrite] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

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
        const [detail, meta] = await Promise.all([getAnnouncementDetail(id), getAnnouncementsMeta()]);
        setAnnouncement(detail);
        setCanWrite(meta.canWrite);
      } catch (e) {
        console.error('공지사항 상세 조회 실패:', e);
        setError('공지사항 상세 정보를 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [id]);

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
              <h2 className="text-2xl leading-tight text-slate-900">{announcement.title}</h2>
              <p className="mt-6 text-sm text-slate-600">
                작성일: {formatDate(announcement.publishedAt ?? announcement.createdAt)} 조회수: {announcement.viewCount}
              </p>

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
                    <button
                      type="button"
                      className="inline-flex items-center gap-2 rounded-xl bg-blue-600 px-6 py-3 text-sm text-white hover:bg-blue-700"
                    >
                      <Pencil className="h-5 w-5" />
                      수정
                    </button>
                    <button
                      type="button"
                      className="inline-flex items-center gap-2 rounded-xl bg-red-600 px-6 py-3 text-sm text-white hover:bg-red-700"
                    >
                      <Trash2 className="h-5 w-5" />
                      삭제
                    </button>
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
