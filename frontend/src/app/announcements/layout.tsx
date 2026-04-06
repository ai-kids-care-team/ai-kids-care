import type { ReactNode } from 'react';

/**
 * 공지사항 목록·상세·작성·수정 화면 공통: 회원가입과 동일하게 전체 UI 80% 축소
 */
export default function AnnouncementsLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen w-full origin-top" style={{ zoom: 0.8 }}>
      {children}
    </div>
  );
}
