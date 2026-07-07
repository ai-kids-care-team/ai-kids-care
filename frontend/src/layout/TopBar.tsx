'use client';

import { ChevronDown, LogIn, LogOut, Settings, UserCircle } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';

// FSD 구조에 맞춘 절대 경로 Import
import { useAppDispatch, useAppSelector } from '@/store/hook';
import { logout } from '@/store/slices/userSlice';
import { Button } from '@/components/shared/ui/button';
import { Badge } from '@/components/shared/ui/badge';
import type { UserRole } from '@/types/user-role';
import { roleLabels } from '@/types/user-role';
import { getMenusForRole } from '@/config/menu';
import { LoginModal } from '@/components/home/LoginModal';
import { SettingsModal } from '@/components/settings/SettingsModal';
import { useLogoutMutation } from '@/services/apis/auth.api';
import { clearCsrf } from '@/services/csrf';
import { UnreadNotificationBadge } from '@/layout/UnreadNotificationBadge';

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
  const unreadNotificationCount = useAppSelector((state) => state.user.unreadNotificationCount);
  const [logoutSession] = useLogoutMutation();
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const isGuest = !hasSession;
  /** ADR-0013: 백엔드 `/menus` 대신 프론트엔드 정적 설정에서 역할별 메뉴를 읽는다. */
  const renderedMenus = getMenusForRole(menuRoleType);

  useEffect(() => {
    const handler = () => setIsLoginModalOpen(true);
    window.addEventListener('open-login-modal', handler);
    return () => window.removeEventListener('open-login-modal', handler);
  }, []);

  // 사용자 드롭다운: 바깥 클릭 시 닫는다(Radix 등 신규 의존성 없이 손수 처리).
  useEffect(() => {
    if (!isMenuOpen) return;
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setIsMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [isMenuOpen]);

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
            {isGuest ? (
              <Button
                variant="ghost"
                size="lg"
                className="gap-2 rounded-lg px-4 text-white transition-colors hover:bg-red-500/80 hover:text-white"
                onClick={() => setIsLoginModalOpen(true)}
              >
                <LogIn className="h-5 w-5" />
                <span className="hidden text-base font-medium sm:inline-block">로그인</span>
              </Button>
            ) : (
              <div className="relative" ref={menuRef}>
                <button
                  type="button"
                  onClick={() => setIsMenuOpen((v) => !v)}
                  className="flex items-center gap-2 rounded-md px-4 py-2 text-base text-white transition-colors hover:bg-white/10"
                  aria-haspopup="menu"
                  aria-expanded={isMenuOpen}
                >
                  <UserCircle className="h-7 w-7" />
                  <span className="font-medium">{username}</span>
                  <ChevronDown
                    className={`h-4 w-4 transition-transform ${isMenuOpen ? 'rotate-180' : ''}`}
                  />
                </button>

                {isMenuOpen && (
                  <div
                    role="menu"
                    className="absolute right-0 z-20 mt-2 w-44 overflow-hidden rounded-lg border border-gray-200 bg-white py-1 text-gray-800 shadow-lg"
                  >
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => {
                        setIsSettingsOpen(true);
                        setIsMenuOpen(false);
                      }}
                      className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm transition-colors hover:bg-gray-100"
                    >
                      <Settings className="h-4 w-4" />
                      개인설정
                    </button>
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => {
                        setIsMenuOpen(false);
                        void handleLogout();
                      }}
                      className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm text-red-600 transition-colors hover:bg-red-50"
                    >
                      <LogOut className="h-4 w-4" />
                      로그아웃
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        <div
          className="border-t border-white/20 bg-[#005640] px-8 py-3 text-white"
          suppressHydrationWarning
        >
          <div className="flex items-center justify-start text-base">
            <div className="flex items-center gap-8">
              {renderedMenus.map((menu) => (
                <Link
                  key={menu.key}
                  href={menu.path}
                  className="relative font-medium transition-colors hover:text-green-300"
                >
                  {menu.label}
                  {menu.key === 'NOTIFICATIONS' && (
                    <UnreadNotificationBadge count={unreadNotificationCount} />
                  )}
                </Link>
              ))}
            </div>
          </div>
        </div>
      </div>
      <LoginModal isOpen={isLoginModalOpen} onClose={() => setIsLoginModalOpen(false)} />
      <SettingsModal isOpen={isSettingsOpen} onClose={() => setIsSettingsOpen(false)} />
    </>
  );
}
