'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Bell, ChevronRight } from 'lucide-react';
import { HeroSlider } from '@/components/home/HeroSlider';
import { Footer } from '@/layout/Footer';
import { useAppSelector } from '@/store/hook';
import { ANNOUNCEMENTS_DEFAULT_SORT, getAnnouncements } from '@/services/apis/announcements.api';
import {
  getAnnouncementBaseDate,
  isAnnouncementNew,
  sortAnnouncementsByNewPriority,
} from '@/components/announcements/functions/announcement-sort';

type HomeAnnouncement = {
  id: number;
  title: string;
  date: string;
  isNew: boolean;
};

const HOME_ANNOUNCEMENTS_COUNT = 10;

function formatDate(value: string) {
  const date = new Date(value);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd}`;
}

export function HomePage() {
  const [announcements, setAnnouncements] = useState<HomeAnnouncement[]>([]);
  const { user } = useAppSelector((state) => state.user);

  useEffect(() => {
    const loadAnnouncements = async () => {
      try {
        const pageData = await getAnnouncements({
          page: 0,
          size: HOME_ANNOUNCEMENTS_COUNT,
          sort: [...ANNOUNCEMENTS_DEFAULT_SORT],
        });
        const now = Date.now();
        const list = sortAnnouncementsByNewPriority(pageData.content, now);
        const mapped = list.map((item) => {
          const baseDate = getAnnouncementBaseDate(item);
          if (!baseDate) {
            return {
              id: item.id,
              title: item.title,
              date: '-',
              isNew: false,
            };
          }

          return {
            id: item.id,
            title: item.title,
            date: formatDate(baseDate),
            isNew: isAnnouncementNew(item, now),
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
      <div className="flex min-h-full w-full flex-col origin-top bg-white" style={{ zoom: 1 }}>
        <main className="mx-auto flex flex-1 w-[min(96vw,132rem)] flex-col justify-center px-[clamp(1rem,1.8vw,2.25rem)] py-[clamp(0.5rem,1.1vh,1.25rem)]">
          <div className="grid w-full items-stretch gap-[clamp(1.25rem,1.8vw,2.25rem)] lg:grid-cols-[minmax(0,2.2fr)_minmax(22rem,1fr)] xl:min-h-[clamp(46rem,80vh,60rem)]">
            <div className="min-w-0 flex h-full flex-col">
              <div className="mb-[clamp(0.875rem,1.1vw,1.25rem)] rounded-[clamp(1.25rem,1.8vw,1.75rem)] bg-gradient-to-r from-blue-50 to-purple-50 p-[clamp(1.25rem,1.8vw,2rem)]">
                <h2 className="mb-2 text-2xl">AI Kids Care란?</h2>
                <p className="text-[clamp(0.95rem,1vw,1.1rem)] leading-relaxed text-gray-700">
                  AI Kids Care는 양육자, 유치원, 행정청, 시스템 관리자가 함께하는 통합 육아 지원 플랫폼입니다.
                  우리 아이들의 건강하고 행복한 성장을 위해 최신 AI 기술과 함께합니다.
                </p>
              </div>

              <div className="min-h-0 flex-1">
                <HeroSlider />
              </div>
            </div>

            <div className="min-w-0">
              <div className="sticky top-6 flex h-full flex-col rounded-[clamp(1.25rem,1.8vw,1.75rem)] bg-white p-[clamp(1.25rem,1.6vw,2rem)] shadow-lg">
                <div className="mb-6 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Bell className="h-7 w-7 text-blue-600" />
                    <h2 className="text-[1.4rem]">공지사항</h2>
                  </div>
                  <Link href="/announcements" className="text-base text-gray-600 hover:text-blue-600">
                    전체보기 +
                  </Link>
                </div>

                <div className="flex-1 divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
                  {announcements.map((announcement) => (
                    <Link
                      key={announcement.id}
                      href={`/announcements/read?id=${announcement.id}`}
                      className="block cursor-pointer px-4 py-3.5 transition-colors hover:bg-slate-50"
                    >
                      <div className="flex items-start gap-2">
                        {announcement.isNew && (
                          <span className="flex-shrink-0 rounded bg-red-500 px-2.5 py-1 text-sm text-white">NEW</span>
                        )}
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-[1.05rem] transition-colors hover:text-blue-600">
                            {announcement.title}
                          </p>
                          <p className="mt-1.5 text-sm text-gray-500">{announcement.date}</p>
                        </div>
                      </div>
                    </Link>
                  ))}
                </div>

                <div className="mt-6 border-t border-gray-200 pt-5">
                  <div className="space-y-2">
                    <Link
                      href="/signup"
                      className="flex items-center justify-between rounded-lg bg-blue-50 p-3 text-blue-700 transition-colors hover:bg-blue-100"
                    >
                      <span className="text-sm">회원가입</span>
                      <ChevronRight className="h-4 w-4" />
                    </Link>

                  </div>
                </div>
              </div>
            </div>
          </div>
        </main>
        <Footer />
      </div>
    </>
  );
}
