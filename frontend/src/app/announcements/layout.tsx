import type { ReactNode } from 'react';

/**
 * 공지사항 목록·상세·작성·수정 화면 공통 배율 설정
 */
export default function AnnouncementsLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen w-full origin-top" style={{ zoom: 1 }}>
      {children}
    </div>
  );
}
