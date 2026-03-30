'use client';

import { LogIn, LogOut, UserCircle } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

// FSD 구조에 맞춘 절대 경로 Import
import { useAppDispatch } from '@/store/hook';
import { logout } from '@/store/slices/userSlice';
import { Button } from '@/components/shared/ui/button';
import { Badge } from '@/components/shared/ui/badge';
import type { UserRole } from '@/types/user-role';
import { roleLabels } from '@/types/user-role';
import { useGetMenusQuery } from '@/services/apis/menu.api';
import { LoginModal } from '@/components/home/LoginModal';

interface TopBarProps {
  currentRole: UserRole;
  username: string;
  /** `/menus?roleType=` — 세션 없으면 ALL */
  menuRoleType: string;
  hasSession: boolean;
}

export function TopBar({ currentRole, username, menuRoleType, hasSession }: TopBarProps) {
  const router = useRouter();
  const dispatch = useAppDispatch();
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const isGuest = !hasSession;
  const { data: menuItems = [] } = useGetMenusQuery(menuRoleType, {
    /** 라우트 이동·창 포커스마다 재조회하면 상단 메뉴가 잠깐 비거나 바뀌는 것처럼 보일 수 있음 */
    refetchOnMountOrArgChange: false,
    refetchOnFocus: false,
    refetchOnReconnect: false,
  });
  const fallbackMenus = [
    { menuId: -1, menuName: '홈', path: '/' },
    { menuId: -2, menuName: '공지사항', path: '/announcements' },
  ];
  const renderedMenus =
    menuItems.length > 0
      ? menuItems
      : fallbackMenus;

  // 게스트도 공개 감사편지는 반드시 볼 수 있어야 함.
  // 백엔드 메뉴 시드가 ANONYMOUS에 대해 비어있을 수 있으므로 프론트에서 최소 링크를 보강.
  const ensuredMenus = (() => {
    if (!isGuest) return renderedMenus;
    const alreadyHasLetters = renderedMenus.some(
      (m) => m.path === '/letters' || m.path === '/appreciationLetter',
    );
    if (alreadyHasLetters) return renderedMenus;

    // "로그인했을 때"와 같은 위치로 보이도록 `공지사항(/announcements)` 바로 앞에 삽입.
    const insertIndex = renderedMenus.findIndex((m) => m.path === '/announcements');
    const appreciationMenu = { menuId: -99, menuName: '감사편지', path: '/letters' };

    if (insertIndex >= 0) {
      return [...renderedMenus.slice(0, insertIndex), appreciationMenu, ...renderedMenus.slice(insertIndex)];
    }

    // 공지사항 메뉴가 없으면 마지막에 추가
    return [...renderedMenus, appreciationMenu];
  })();

  useEffect(() => {
    const handler = () => setIsLoginModalOpen(true);
    window.addEventListener('open-login-modal', handler);
    return () => window.removeEventListener('open-login-modal', handler);
  }, []);

  const handleLogout = () => {
    // 전역 상태 초기화
    dispatch(logout());

    // 모든 인증 관련 로컬스토리지 키 정리 (예전/현재 키 모두)
    if (typeof window !== 'undefined') {
      try {
        localStorage.removeItem('user');
        localStorage.removeItem('token');
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
      } catch {
        // 로컬스토리지가 막혀 있어도 무시
      }
    }

    // AI Kids Care 홈페이지(메인 화면)로 이동
    router.push('/');
  };

  return (
    <>
      <div className="relative z-10 shadow-md">
        <div className="bg-[#006b52] text-white px-6 py-3 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Link href="/" className="text-lg font-semibold tracking-tight hover:text-green-200 transition-colors">
              AI Kids Care
            </Link>
            {!isGuest && (
              <Badge className="bg-white/20 hover:bg-white/30 text-white border-white/40 font-normal">
                {roleLabels[currentRole]}
              </Badge>
            )}
          </div>

          <div className="flex items-center gap-2">
            {!isGuest && (
              <div className="flex items-center gap-2 px-3 py-1.5 text-sm text-white rounded-md">
                <UserCircle className="w-5 h-5" />
                <span className="font-medium">{username}</span>
              </div>
            )}

            <div className="flex items-center gap-1 ml-1 pl-3 border-l border-white/20">
              <Button
                  variant="ghost"
                  size="sm"
                  className="text-white hover:bg-red-500/80 hover:text-white gap-1.5 px-3 rounded-md transition-colors"
                  onClick={isGuest ? () => setIsLoginModalOpen(true) : handleLogout}
              >
                {isGuest ? <LogIn className="w-4 h-4" /> : <LogOut className="w-4 h-4" />}
                <span className="text-sm font-medium hidden sm:inline-block">
                  {isGuest ? '로그인' : '로그아웃'}
                </span>
              </Button>
            </div>
          </div>
        </div>

        <div
          className="bg-[#005640] text-white px-6 py-2 border-t border-white/20"
          suppressHydrationWarning
        >
          <div className="flex items-center justify-start text-sm">
            <div className="flex items-center gap-6">
              {ensuredMenus.map((menu) => {
                if (!menu.path) return null;
                return (
                  <Link key={menu.menuId} href={menu.path} className="hover:text-green-300 transition-colors">
                    {menu.menuName}
                  </Link>
                );
              })}
            </div>
          </div>
        </div>
      </div>
      <LoginModal isOpen={isLoginModalOpen} onClose={() => setIsLoginModalOpen(false)} />
    </>
  );
}