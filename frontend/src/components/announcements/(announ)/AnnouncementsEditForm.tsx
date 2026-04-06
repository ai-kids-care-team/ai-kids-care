'use client';

import Link from 'next/link';
import { Bell } from 'lucide-react';
import { useAnnouncementsEdit } from '@/components/announcements/functions/useAnnouncementsEdit';
import { describeAnnouncementEditorRolesKorean } from '@/types/user-role';

export function AnnouncementsEditForm() {
  const {
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
    authorIdHiddenValue,
    canWrite,
    loadingAnnouncement,
    submitting,
    error,
    publishedAtError,
    handleSubmit,
  } = useAnnouncementsEdit();

  const loading = loadingAnnouncement;

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-5xl">
        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-8 border-b border-gray-200 pb-6">
            <div className="flex items-center gap-3">
              <Bell className="h-7 w-7 text-[#006b52]" />
              <h2 className="text-3xl">공지사항 수정</h2>
            </div>
            <p className="mt-2 text-sm text-gray-500">공지사항 내용을 수정한 뒤 저장해주세요.</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            <input type="hidden" name="authorId" value={authorIdHiddenValue} readOnly />
            {loading && <p className="text-sm text-gray-500">수정 정보를 불러오는 중입니다.</p>}
            {!loading && !canWrite && (
              <p className="rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-700">
                공지 수정은 <span className="font-medium">{describeAnnouncementEditorRolesKorean()}</span> 계정에서만
                가능합니다. 해당 권한이 있는 계정으로 로그인했는지 확인해 주세요.
              </p>
            )}

            <div>
              <label className="mb-2 block text-sm font-medium text-slate-700">제목</label>
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                maxLength={200}
                placeholder="공지사항 제목을 입력하세요"
                disabled={loading || !canWrite || submitting}
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-slate-700">내용</label>
              <textarea
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder="공지사항 내용을 입력하세요"
                rows={10}
                disabled={loading || !canWrite || submitting}
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-3 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div className="grid gap-5 md:grid-cols-2">
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">중요공지사항</label>
                <label className="inline-flex items-center gap-2 text-sm text-slate-700">
                  <input
                    type="radio"
                    name="isPinned"
                    checked={isPinned}
                    onChange={() => setIsPinned(true)}
                    disabled={loading || !canWrite || submitting}
                  />
                  예
                </label>
                <label className="ml-4 inline-flex items-center gap-2 text-sm text-slate-700">
                  <input
                    type="radio"
                    name="isPinned"
                    checked={!isPinned}
                    onChange={() => setIsPinned(false)}
                    disabled={loading || !canWrite || submitting}
                  />
                  아니오
                </label>
              </div>

              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">공지 중 중요공지 종료일</label>
                <input
                  type="datetime-local"
                  value={pinnedUntil}
                  onChange={(e) => setPinnedUntil(e.target.value)}
                  disabled={loading || !canWrite || submitting}
                  className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-slate-700">게시 일시</label>
              <input
                type="datetime-local"
                value={publishedAt}
                onChange={(e) => setPublishedAt(e.target.value)}
                disabled={loading || !canWrite || submitting}
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              />
              {publishedAtError && <p className="mt-1 text-sm text-red-500">{publishedAtError}</p>}
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-slate-700">게시 구분(status)</label>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value as 'ACTIVE' | 'PENDING' | 'DISABLED')}
                disabled={loading || !canWrite || submitting}
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              >
                {statusOptions.map((option) => (
                  <option key={option.code} value={option.code}>
                    {option.codeName} ({option.code})
                  </option>
                ))}
              </select>
            </div>

            <div className="grid gap-5 md:grid-cols-2">
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">게시 시작일</label>
                <input
                  type="datetime-local"
                  value={startsAt}
                  onChange={(e) => setStartsAt(e.target.value)}
                  disabled={loading || !canWrite || submitting}
                  className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">게시 종료일</label>
                <input
                  type="datetime-local"
                  value={endsAt}
                  onChange={(e) => setEndsAt(e.target.value)}
                  disabled={loading || !canWrite || submitting}
                  className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>

            {error && <p className="text-sm text-red-500">{error}</p>}

            <div className="flex items-center justify-end gap-2">
              <Link
                href={Number.isFinite(id) && id > 0 ? `/announcements/read?id=${id}` : '/announcements'}
                className="rounded-lg border border-slate-300 px-4 py-2 text-slate-700 transition-colors hover:bg-slate-100"
              >
                취소
              </Link>
              <button
                type="submit"
                disabled={loading || !canWrite || submitting}
                className="rounded-lg bg-blue-600 px-4 py-2 font-medium text-white transition-colors hover:bg-blue-700"
              >
                {submitting ? '수정 중...' : '수정 저장'}
              </button>
            </div>
          </form>
        </div>
      </main>
    </div>
  );
}
