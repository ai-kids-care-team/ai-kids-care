'use client';

import { LogIn, LogOut, Settings, UserCircle, Bell } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

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
import type { UserRole } from '@/types/anomaly';
import { roleLabels } from '@/types/anomaly';
import { useGetMenusQuery } from '@/services/apis/menu.api';

interface TopBarProps {
  currentRole: UserRole;
  username: string;
  onRoleChange?: (role: UserRole) => void;
}

const roles: UserRole[] = ['super_admin', 'system_admin', 'admin', 'teacher', 'guardian'];
const KINDERGARTEN_ID = '1';

const mapFrontendRoleToMenuRole = (role: UserRole): string => {
  switch (role) {
    case 'teacher':
      return 'TEACHER';
    case 'admin':
      return 'ADMIN';
    case 'system_admin':
      return 'PLATFORM_IT_ADMIN';
    case 'super_admin':
      return 'SUPERADMIN';
    case 'guardian':
    default:
      return 'GUARDIAN';
  }
};

export function TopBar({ currentRole, username, onRoleChange }: TopBarProps) {
  const router = useRouter();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.user);
  const isGuest = !user;

  // RTK Query를 이용한 알림 데이터 패칭 (30초마다 갱신)
  const { data: notifications = [] } = useGetNotificationsQuery(KINDERGARTEN_ID, {
    skip: !user,
    pollingInterval: 30000,
  });

  const [markAllAsReadApi] = useMarkAllNotificationsAsReadMutation();
  const unreadCount = notifications.filter((n: any) => !n.isRead).length;
  const menuRoleType = isGuest ? 'ALL' : mapFrontendRoleToMenuRole(currentRole);
  console.log('[TopBar] menuRoleType:', menuRoleType);
  const { data: menuItems = [] } = useGetMenusQuery(menuRoleType);
  const fallbackMenus = [
    { menuId: -1, menuName: '대시보드', path: '/dashboard' },
    { menuId: -2, menuName: '공지사항', path: '/notices' },
  ];
  const renderedMenus = menuItems.length > 0 ? menuItems : fallbackMenus;

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
    dispatch(logout());
    router.push('/login');
  };

  return (
      <div className="relative z-10 shadow-md">
        <div className="bg-[#006b52] text-white px-6 py-3 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <h1 className="text-lg font-semibold tracking-tight">햇살유치원 CCTV 관리</h1>
            <Badge className="bg-white/20 hover:bg-white/30 text-white border-white/40 font-normal">
              {roleLabels[currentRole]}
            </Badge>
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
                  onClick={isGuest ? () => router.push('/login') : handleLogout}
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

  );
}