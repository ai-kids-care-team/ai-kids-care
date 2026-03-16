'use client';

import Link from 'next/link';
import { useAnnouncementsWrite } from '../model/useAnnouncementsWrite';

export function AnnouncementsWriteForm() {
  const {
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
  } = useAnnouncementsWrite();

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-5xl">
        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-8 border-b border-gray-200 pb-6">
            <h2 className="text-3xl">공지사항 글쓰기</h2>
            <p className="mt-2 text-sm text-gray-500">공지사항 제목과 내용을 입력해주세요.</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            {loadingMeta && <p className="text-sm text-gray-500">권한 및 코드 정보를 불러오는 중입니다.</p>}
            {!loadingMeta && !canWrite && (
              <p className="rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-700">
                현재 계정은 공지사항 작성 권한이 없습니다.
              </p>
            )}

            <div>
              <label className="mb-2 block text-sm font-medium text-slate-700">제목</label>
              <input
                type="text"
                name="title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="공지사항 제목을 입력하세요"
                disabled={!canWrite || submitting}
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-slate-700">내용</label>
              <textarea
                name="content"
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder="공지사항 내용을 입력하세요"
                rows={10}
                disabled={!canWrite || submitting}
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
                    disabled={!canWrite || submitting}
                  />
                  예
                </label>
                <label className="ml-4 inline-flex items-center gap-2 text-sm text-slate-700">
                  <input
                    type="radio"
                    name="isPinned"
                    checked={!isPinned}
                    onChange={() => setIsPinned(false)}
                    disabled={!canWrite || submitting}
                  />
                  아니오
                </label>
              </div>

              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">공시일(중요공지 종료일)</label>
                <input
                  type="datetime-local"
                  value={pinnedUntil}
                  onChange={(e) => setPinnedUntil(e.target.value)}
                  disabled={!canWrite || submitting}
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
                disabled={!canWrite || submitting}
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            {canWrite && (
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">게시 구분(status)</label>
                <select
                  value={status}
                  onChange={(e) => setStatus(e.target.value as 'ACTIVE' | 'PENDING' | 'DISABLED')}
                  disabled={submitting}
                  className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                >
                  {statusOptions.map((option) => (
                    <option key={option.code} value={option.code}>
                      {option.codeName} ({option.code})
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="grid gap-5 md:grid-cols-2">
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">게시 시작일</label>
                <input
                  type="datetime-local"
                  value={startsAt}
                  onChange={(e) => setStartsAt(e.target.value)}
                  disabled={!canWrite || submitting}
                  className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">게시 종료일</label>
                <input
                  type="datetime-local"
                  value={endsAt}
                  onChange={(e) => setEndsAt(e.target.value)}
                  disabled={!canWrite || submitting}
                  className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>

            {error && <p className="text-sm text-red-500">{error}</p>}

            <div className="flex items-center justify-end gap-2">
              <Link
                href="/announcements"
                className="rounded-lg border border-slate-300 px-4 py-2 text-slate-700 transition-colors hover:bg-slate-100"
              >
                목록으로
              </Link>
              <button
                type="submit"
                disabled={!canWrite || submitting}
                className="rounded-lg bg-[#006b52] px-4 py-2 font-medium text-white transition-colors hover:bg-[#005640]"
              >
                {submitting ? '저장 중...' : '저장'}
              </button>
            </div>
          </form>
        </div>
      </main>
    </div>
  );
}
