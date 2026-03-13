'use client';

import Link from 'next/link';
import { Bell, Plus } from 'lucide-react';

const notices = [
  { id: 1, title: '2026년 AI Kids Care 서비스 오픈 안내', date: '2026.03.10', isNew: true, views: 152 },
  { id: 2, title: '개인정보 처리방침 개정 안내', date: '2026.03.08', isNew: true, views: 98 },
  { id: 3, title: '회원가입 시 주의사항 안내', date: '2026.03.05', isNew: false, views: 245 },
  { id: 4, title: '양육자 및 유치원 등록 절차 안내', date: '2026.03.01', isNew: false, views: 189 },
  { id: 5, title: '시스템 점검 일정 공지', date: '2026.02.28', isNew: false, views: 321 },
];

export function NoticesListPage() {
  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="max-w-5xl mx-auto">
        <div className="bg-white rounded-2xl shadow-lg p-8">
          <div className="flex items-center justify-between mb-8 pb-6 border-b border-gray-200">
            <div className="flex items-center gap-3">
              <Bell className="w-7 h-7 text-[#006b52]" />
              <h2 className="text-3xl">공지사항</h2>
            </div>
            <button className="flex items-center gap-2 px-5 py-2.5 bg-[#006b52] text-white rounded-lg hover:bg-[#005640] transition-colors">
              <Plus className="w-5 h-5" />
              글쓰기
            </button>
          </div>

          <div className="space-y-3">
            {notices.map((notice) => (
              <Link
                key={notice.id}
                href="/events"
                className="block p-5 border border-gray-200 rounded-lg hover:bg-emerald-50 hover:border-emerald-300 transition-all"
              >
                <div className="flex items-start gap-3">
                  {notice.isNew && (
                    <span className="flex-shrink-0 px-2.5 py-1 bg-red-500 text-white text-xs rounded">NEW</span>
                  )}
                  <div className="flex-1 min-w-0">
                    <p className="text-lg hover:text-[#006b52] transition-colors">{notice.title}</p>
                    <div className="flex items-center gap-4 mt-2 text-sm text-gray-500">
                      <span>{notice.date}</span>
                      <span>조회수: {notice.views}</span>
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
