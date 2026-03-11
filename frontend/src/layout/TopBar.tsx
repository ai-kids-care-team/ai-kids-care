'use client';

import { LogOut, Settings, UserCircle, Bell } from 'lucide-react';
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

interface TopBarProps {
  currentRole: UserRole;
  username: string;
  onRoleChange?: (role: UserRole) => void;
}

const roles: UserRole[] = ['super_admin', 'system_admin', 'admin', 'teacher', 'guardian'];
const KINDERGARTEN_ID = '1';

export function TopBar({ currentRole, username, onRoleChange }: TopBarProps) {
  const router = useRouter();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.user);

  // RTK Query를 이용한 알림 데이터 패칭 (30초마다 갱신)
  const { data: notifications = [] } = useGetNotificationsQuery(KINDERGARTEN_ID, {
    skip: !user,
    pollingInterval: 30000,
  });

  const [markAllAsReadApi] = useMarkAllNotificationsAsReadMutation();
  const unreadCount = notifications.filter((n: any) => !n.isRead).length;

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

  if (!user) return null;

  return (
      <div className="bg-purple-600 text-white px-6 py-3 flex items-center justify-between shadow-md relative z-10">
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
                  <span className="absolute top-1 right-1.5 w-2 h-2 bg-red-500 rounded-full border border-purple-600 animate-pulse"></span>
                )}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-80 pb-2">
              <div className="flex items-center justify-between px-4 py-3">
                <DropdownMenuLabel className="p-0 font-bold text-slate-800 text-base">최근 알림</DropdownMenuLabel>
                {unreadCount > 0 && (
                  <button onClick={handleMarkAllAsRead} className="text-xs text-purple-600 hover:text-purple-800 font-medium transition-colors">
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
                      className={`flex flex-col items-start gap-1 p-3 border-b border-slate-100 last:border-0 hover:bg-slate-50 transition-colors cursor-pointer ${notif.isRead ? 'opacity-60' : 'bg-purple-50/50'}`}
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
                          className={currentRole === role ? 'bg-purple-50 font-medium text-purple-700' : ''}
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
                onClick={handleLogout}
            >
              <LogOut className="w-4 h-4" />
              <span className="text-sm font-medium hidden sm:inline-block">로그아웃</span>
            </Button>
          </div>
        </div>
      </div>
  );
}