'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Bell, ChevronRight } from 'lucide-react';
import { HeroSlider } from '@/components/home/HeroSlider';
import { LoginModal } from '@/components/home/LoginModal';
import { Footer } from '@/layout/Footer';
import { useAppSelector } from '@/store/hook';
import { getAnnouncements } from '@/services/apis/announcements.api';


type HomeAnnouncement = {
  id: number;
  title: string;
  date: string;
  isNew: boolean;
};

function formatDate(value: string) {
  const date = new Date(value);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd}`;
}

export function HomePage() {
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const [announcements, setAnnouncements] = useState<HomeAnnouncement[]>([]);
  const { user } = useAppSelector((state) => state.user);

  useEffect(() => {
    const loadAnnouncements = async () => {
      try {
        const list = await getAnnouncements();
        const now = Date.now();
        const mapped = list
          .sort((a, b) => {
            const aBase = a.publishedAt ?? a.createdAt;
            const bBase = b.publishedAt ?? b.createdAt;
            return new Date(bBase).getTime() - new Date(aBase).getTime();
          })
          .slice(0, 5)
          .map((item) => {
            const baseDate = item.publishedAt ?? item.createdAt;
            return {
              id: item.id,
              title: item.title,
              date: formatDate(baseDate),
              isNew: now - new Date(baseDate).getTime() <= 7 * 24 * 60 * 60 * 1000,
            };
          });
        setAnnouncements(mapped);
      } catch (e) {
        console.error('홈 공지사항 조회 실패:', e);
        setAnnouncements([]);
      }
    };

    void loadAnnouncements();
  }, []);

  return (
    <>
      <div className="min-h-screen bg-white">
        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
          <div className="grid lg:grid-cols-3 gap-8">
            <div className="lg:col-span-2 flex flex-col">
              <div className="bg-gradient-to-r from-blue-50 to-purple-50 rounded-2xl p-6 mb-4">
                <h2 className="text-2xl mb-2">AI Kids Care란?</h2>
                <p className="text-gray-700 leading-relaxed">
                  AI Kids Care는 양육자, 유치원, 행정청, 시스템 관리자가 함께하는 통합 육아 지원 플랫폼입니다.
                  우리 아이들의 건강하고 행복한 성장을 위해 최신 AI 기술과 함께합니다.
                </p>
              </div>

              <div className="flex-1">
                <HeroSlider />
              </div>
            </div>

            <div className="lg:col-span-1">
              <div className="bg-white rounded-2xl shadow-lg p-6 sticky top-8">
                <div className="flex items-center justify-between mb-6">
                  <div className="flex items-center gap-2">
                    <Bell className="w-6 h-6 text-blue-600" />
                    <h2 className="text-xl">공지사항</h2>
                  </div>
                  <Link href="/announcements" className="text-sm text-gray-600 hover:text-blue-600">
                    전체보기 +
                  </Link>
                </div>

                <div className="divide-y divide-slate-200 rounded-lg border border-slate-200">
                  {announcements.map((announcement) => (
                    <Link
                      key={announcement.id}
                      href={`/announcements/read?id=${announcement.id}`}
                      className="block cursor-pointer px-3 py-3 transition-colors hover:bg-slate-50"
                    >
                      <div className="flex items-start gap-2">
                        {announcement.isNew && (
                          <span className="flex-shrink-0 px-2 py-0.5 bg-red-500 text-white text-xs rounded">NEW</span>
                        )}
                        <div className="flex-1 min-w-0">
                          <p className="text-sm truncate hover:text-blue-600 transition-colors">{announcement.title}</p>
                          <p className="text-xs text-gray-500 mt-1">{announcement.date}</p>
                        </div>
                      </div>
                    </Link>
                  ))}
                </div>

                <div className="mt-8 pt-6 border-t border-gray-200">
                  <h3 className="text-sm mb-4">빠른 링크</h3>
                  <div className="space-y-2">
                    <Link
                      href="/signup"
                      className="flex items-center justify-between p-3 bg-blue-50 text-blue-700 rounded-lg hover:bg-blue-100 transition-colors"
                    >
                      <span className="text-sm">회원가입</span>
                      <ChevronRight className="w-4 h-4" />
                    </Link>

                    {!user ? (
                      <button
                        className="w-full flex items-center justify-between p-3 bg-gray-50 text-gray-700 rounded-lg hover:bg-gray-100 transition-colors"
                        onClick={() => setIsLoginModalOpen(true)}
                      >
                        <span className="text-sm">로그인</span>
                        <ChevronRight className="w-4 h-4" />
                      </button>
                    ) : (
                      <Link
                        href="/dashboard"
                        className="flex items-center justify-between p-3 bg-gray-50 text-gray-700 rounded-lg hover:bg-gray-100 transition-colors"
                      >
                        <span className="text-sm">대시보드</span>
                        <ChevronRight className="w-4 h-4" />
                      </Link>
                    )}

                    <button className="w-full flex items-center justify-between p-3 bg-gray-50 text-gray-700 rounded-lg hover:bg-gray-100 transition-colors">
                      <span className="text-sm">고객센터</span>
                      <ChevronRight className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </main>

        <LoginModal isOpen={isLoginModalOpen} onClose={() => setIsLoginModalOpen(false)} />
        <Footer />
      </div>
    </>
  );
}
