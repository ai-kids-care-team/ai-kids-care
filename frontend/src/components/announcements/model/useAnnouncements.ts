'use client';

export type AnnouncementItem = {
  id: number;
  title: string;
  date: string;
  isNew: boolean;
  views: number;
  href: string;
};

const ANNOUNCEMENT_MOCK_DATA: AnnouncementItem[] = [
  { id: 1, title: '2026년 AI Kids Care 서비스 오픈 안내', date: '2026.03.10', isNew: true, views: 152, href: '/events' },
  { id: 2, title: '개인정보 처리방침 개정 안내', date: '2026.03.08', isNew: true, views: 98, href: '/events' },
  { id: 3, title: '회원가입 시 주의사항 안내', date: '2026.03.05', isNew: false, views: 245, href: '/events' },
  { id: 4, title: '양육자 및 유치원 등록 절차 안내', date: '2026.03.01', isNew: false, views: 189, href: '/events' },
  { id: 5, title: '시스템 점검 일정 공지', date: '2026.02.28', isNew: false, views: 321, href: '/events' },
];

export function useAnnouncements() {
  // TODO: 추후 API 연동 시 이 훅에서 조회/페이징/정렬을 처리
  return {
    announcements: ANNOUNCEMENT_MOCK_DATA,
  };
}
