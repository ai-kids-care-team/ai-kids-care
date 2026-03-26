'use client';

import { LogIn, LogOut, Settings, UserCircle, Bell } from 'lucide-react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

// FSD 구조에 맞춘 절대 경로 Import
import { useAppSelector, useAppDispatch } from '@/store/hook';
import { logout } from '@/store/slices/userSlice';
import { useGetNotificationsQuery, useMarkAllNotificationsAsReadMutation } from '@/services/apis/user.api';
import { Button } from '@/components/shared/ui/button';
import { Badge } from '@/components/shared/ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/shared/ui/dropdown-menu';
import type { UserRole } from '@/types/user-role';
import { menuApiRoleType, roleLabels, USER_ROLES } from '@/types/user-role';
import { useGetMenusQuery } from '@/services/apis/menu.api';
import { LoginModal } from '@/components/home/LoginModal';

interface TopBarProps {
  currentRole: UserRole;
  username: string;
  onRoleChange?: (role: UserRole) => void;
}

const roles = USER_ROLES;
const KINDERGARTEN_ID = '1';

export function TopBar({ currentRole, username, onRoleChange }: TopBarProps) {
  const router = useRouter();
  const pathname = usePathname();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.user);
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const isGuest = !user;
  const isHome = pathname === '/';

  // RTK Query: 홈(/)에서는 알림 API 호출하지 않음 (다른 화면에서만 패칭, 30초 폴링)
  const { data: notifications = [] } = useGetNotificationsQuery(KINDERGARTEN_ID, {
    skip: !user || isHome,
    pollingInterval: 30000,
  });

  const [markAllAsReadApi] = useMarkAllNotificationsAsReadMutation();
  const unreadCount = notifications.filter((n: any) => !n.isRead).length;
  const menuRoleType = isGuest ? 'ALL' : menuApiRoleType(currentRole);
  const { data: menuItems = [] } = useGetMenusQuery(menuRoleType);
  const fallbackMenus = [
    { menuId: -1, menuName: '대시보드', path: '/dashboard' },
    { menuId: -2, menuName: '공지사항', path: '/announcements' },
  ];
  const renderedMenus = menuItems.length > 0 ? menuItems : fallbackMenus;

  useEffect(() => {
    const handler = () => setIsLoginModalOpen(true);
    window.addEventListener('open-login-modal', handler);
    return () => window.removeEventListener('open-login-modal', handler);
  }, []);

  const handleMarkAllAsRead = async () => {
    if (unreadCount > 0) {
      try {
        await markAllAsReadApi(KINDERGARTEN_ID).unwrap();
      } catch (error) {
        console.error('알림 읽음 처리 실패', error);
      }
    }
  };

  const handleLogout = () => {
    // 전역 상태 초기화
    dispatch(logout());

    // 모든 인증 관련 로컬스토리지 키 정리 (예전/현재 키 모두)
    if (typeof window !== 'undefined') {
      try {
        localStorage.removeItem('user');
        localStorage.removeItem('token');
        localStorage.removeItem('cctv_user');
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
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="sm" className="relative text-white hover:bg-white/20 mr-1 rounded-full w-9 h-9 p-0">
                  <Bell className="w-5 h-5" />
                  {unreadCount > 0 && (
                    <span className="absolute top-1 right-1.5 w-2 h-2 bg-red-500 rounded-full border border-[#006b52] animate-pulse"></span>
                  )}
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-80 pb-2">
                <div className="flex items-center justify-between px-4 py-3">
                  <DropdownMenuLabel className="p-0 font-bold text-slate-800 text-base">최근 알림</DropdownMenuLabel>
                  {unreadCount > 0 && (
                    <button onClick={handleMarkAllAsRead} className="text-xs text-[#006b52] hover:text-[#00503d] font-medium transition-colors">
                      모두 읽음
                    </button>
                  )}
                </div>
                <DropdownMenuSeparator className="mb-0" />

                <div className="max-h-[300px] overflow-y-auto">
                  {notifications.length === 0 ? (
                    <div className="py-8 text-center text-sm text-slate-500">
                      새로운 알림이 없습니다.
                    </div>
                  ) : (
                    notifications.map((notif: any) => (
                      <div
                        key={notif.id}
                        className={`flex flex-col items-start gap-1 p-3 border-b border-slate-100 last:border-0 hover:bg-slate-50 transition-colors cursor-pointer ${notif.isRead ? 'opacity-60' : 'bg-emerald-50/70'}`}
                      >
                        <div className="flex justify-between w-full items-start gap-2">
                          <span className="font-semibold text-sm text-slate-900 leading-tight">
                            {notif.title}
                          </span>
                          <span className="text-[10px] text-slate-400 whitespace-nowrap pt-0.5">
                            {new Date(notif.createdAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}
                          </span>
                        </div>
                        <span className="text-xs text-slate-600 line-clamp-2 leading-relaxed">
                          {notif.message}
                        </span>
                      </div>
                    ))
                  )}
                </div>
              </DropdownMenuContent>
            </DropdownMenu>

            {onRoleChange && (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <button className="flex items-center gap-2 px-3 py-1.5 text-sm text-white hover:bg-white/20 rounded-md transition-colors">
                      <UserCircle className="w-5 h-5" />
                      <span className="font-medium">{username}</span>
                    </button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end" className="w-56">
                    <DropdownMenuLabel>데모용 역할 전환</DropdownMenuLabel>
                    <DropdownMenuSeparator />
                    {roles.map((role) => (
                        <DropdownMenuItem
                            key={role}
                            onClick={() => onRoleChange(role)}
                            className={currentRole === role ? 'bg-emerald-50 font-medium text-[#006b52]' : ''}
                        >
                          {roleLabels[role]}
                          {currentRole === role && ' ✓'}
                        </DropdownMenuItem>
                    ))}
                    <DropdownMenuSeparator />
                    <DropdownMenuLabel className="text-xs text-slate-400 font-normal">
                      실제 운영시 로그인 정보로 자동 결정됩니다
                    </DropdownMenuLabel>
                  </DropdownMenuContent>
                </DropdownMenu>
            )}

            <div className="flex items-center gap-1 ml-1 pl-3 border-l border-white/20">
              <Button variant="ghost" size="sm" className="text-white hover:bg-white/20 w-9 h-9 p-0 rounded-full">
                <Settings className="w-4 h-4" />
              </Button>
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

        <div className="bg-[#005640] text-white px-6 py-2 border-t border-white/20">
          <div className="flex items-center justify-start text-sm">
            <div className="flex items-center gap-6">
              {renderedMenus.map((menu) => {
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