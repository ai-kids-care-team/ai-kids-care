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
import { getMenusForRole } from '@/config/menu';
import { LoginModal } from '@/components/home/LoginModal';
import { useLogoutMutation } from '@/services/apis/auth.api';
import { clearCsrf } from '@/services/csrf';

interface TopBarProps {
  currentRole: UserRole;
  username: string;
  /** 역할 키 — 세션 없으면 'ANONYMOUS'. ADR-0013 정적 메뉴 설정의 키. */
  menuRoleType: string;
  hasSession: boolean;
}

export function TopBar({ currentRole, username, menuRoleType, hasSession }: TopBarProps) {
  const router = useRouter();
  const dispatch = useAppDispatch();
  const [logoutSession] = useLogoutMutation();
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const isGuest = !hasSession;
  /** ADR-0013: 백엔드 `/menus` 대신 프론트엔드 정적 설정에서 역할별 메뉴를 읽는다. */
  const renderedMenus = getMenusForRole(menuRoleType);

  useEffect(() => {
    const handler = () => setIsLoginModalOpen(true);
    window.addEventListener('open-login-modal', handler);
    return () => window.removeEventListener('open-login-modal', handler);
  }, []);

  const handleLogout = async () => {
    try {
      await logoutSession().unwrap();
    } finally {
      clearCsrf();
      dispatch(logout());
      router.push('/');
    }
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
                  onClick={isGuest ? () => setIsLoginModalOpen(true) : () => void handleLogout()}
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
              {renderedMenus.map((menu) => (
                <Link key={menu.key} href={menu.path} className="font-medium transition-colors hover:text-green-300">
                  {menu.label}
                </Link>
              ))}
            </div>
          </div>
        </div>
      </div>
      <LoginModal isOpen={isLoginModalOpen} onClose={() => setIsLoginModalOpen(false)} />
    </>
  );
}
