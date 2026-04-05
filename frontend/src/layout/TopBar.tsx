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
  const renderedMenus = menuItems.length > 0 ? menuItems : fallbackMenus;

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
        <div className="flex items-center justify-between bg-[#006b52] px-8 py-[1.15rem] text-white">
          <div className="flex items-center gap-6">
            <Link href="/" className="text-[1.7rem] font-semibold tracking-tight transition-colors hover:text-green-200">
              AI Kids Care
            </Link>
            {!isGuest && (
              <Badge className="border-white/40 bg-white/20 px-3 py-1 text-sm font-normal text-white hover:bg-white/30">
                {roleLabels[currentRole]}
              </Badge>
            )}
          </div>

          <div className="flex items-center gap-3">
            {!isGuest && (
              <div className="flex items-center gap-3 rounded-md px-4 py-2 text-base text-white">
                <UserCircle className="h-7 w-7" />
                <span className="font-medium">{username}</span>
              </div>
            )}

            <div className="ml-2 flex items-center gap-2 border-l border-white/20 pl-4">
              <Button
                  variant="ghost"
                  size="lg"
                  className="gap-2 rounded-lg px-4 text-white transition-colors hover:bg-red-500/80 hover:text-white"
                  onClick={isGuest ? () => setIsLoginModalOpen(true) : handleLogout}
              >
                {isGuest ? <LogIn className="h-5 w-5" /> : <LogOut className="h-5 w-5" />}
                <span className="hidden text-base font-medium sm:inline-block">
                  {isGuest ? '로그인' : '로그아웃'}
                </span>
              </Button>
            </div>
          </div>
        </div>

        <div
          className="border-t border-white/20 bg-[#005640] px-8 py-3 text-white"
          suppressHydrationWarning
        >
          <div className="flex items-center justify-start text-base">
            <div className="flex items-center gap-8">
              {renderedMenus.map((menu) => {
                if (!menu.path) return null;
                return (
                  <Link key={menu.menuId} href={menu.path} className="font-medium transition-colors hover:text-green-300">
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
