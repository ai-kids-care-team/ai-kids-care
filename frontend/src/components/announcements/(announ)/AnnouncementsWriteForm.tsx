'use client';

import Link from 'next/link';
import { useAnnouncementsWrite } from '../model/useAnnouncementsWrite';

export function AnnouncementsWriteForm() {
  const { title, setTitle, content, setContent, error, handleSubmit } = useAnnouncementsWrite();

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-5xl">
        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-8 border-b border-gray-200 pb-6">
            <h2 className="text-3xl">공지사항 글쓰기</h2>
            <p className="mt-2 text-sm text-gray-500">공지사항 제목과 내용을 입력해주세요.</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="mb-2 block text-sm font-medium text-slate-700">제목</label>
              <input
                type="text"
                name="title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="공지사항 제목을 입력하세요"
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
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-3 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              />
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
                className="rounded-lg bg-[#006b52] px-4 py-2 font-medium text-white transition-colors hover:bg-[#005640]"
              >
                저장
              </button>
            </div>
          </form>
        </div>
      </main>
    </div>
  );
}
