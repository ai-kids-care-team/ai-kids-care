'use client';

import Link from 'next/link';
import { Bell, Plus } from 'lucide-react';
import type { AnnouncementItem } from './model/useAnnouncements';

type AnnouncementsListFormProps = {
  announcements: AnnouncementItem[];
};

export function AnnouncementsListForm({ announcements }: AnnouncementsListFormProps) {
  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-5xl">
        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-8 flex items-center justify-between border-b border-gray-200 pb-6">
            <div className="flex items-center gap-3">
              <Bell className="h-7 w-7 text-[#006b52]" />
              <h2 className="text-3xl">공지사항</h2>
            </div>
            <Link
              href="/announcements/write"
              className="flex items-center gap-2 rounded-lg bg-[#006b52] px-5 py-2.5 text-white transition-colors hover:bg-[#005640]"
            >
              <Plus className="h-5 w-5" />
              글쓰기
            </Link>
          </div>

          <div className="space-y-3">
            {announcements.map((announcement) => (
              <Link
                key={announcement.id}
                href={announcement.href}
                className="block rounded-lg border border-gray-200 p-5 transition-all hover:border-emerald-300 hover:bg-emerald-50"
              >
                <div className="flex items-start gap-3">
                  {announcement.isNew && (
                    <span className="flex-shrink-0 rounded bg-red-500 px-2.5 py-1 text-xs text-white">NEW</span>
                  )}
                  <div className="min-w-0 flex-1">
                    <p className="text-lg transition-colors hover:text-[#006b52]">{announcement.title}</p>
                    <div className="mt-2 flex items-center gap-4 text-sm text-gray-500">
                      <span>{announcement.date}</span>
                      <span>조회수: {announcement.views}</span>
                    </div>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </main>
    </div>
  );
}
