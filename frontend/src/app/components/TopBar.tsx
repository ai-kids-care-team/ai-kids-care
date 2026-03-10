'use client';

import { useState, useEffect } from 'react';
import { LogOut, Settings, UserCircle, Bell } from 'lucide-react'; // 👈 Bell 아이콘 추가
import { useRouter } from 'next/navigation';
import { useAuth } from '@/app/context/AuthContext';
import { apiClient } from '@/app/api/apiClient'; // 👈 API 클라이언트 추가
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from './ui/dropdown-menu';
import type { UserRole } from '../types/anomaly';
import { roleLabels } from '../types/anomaly';
import type { Notification } from '@/app/types/api'; // (선택) 앞서 만든 타입이 있다면 활용

interface TopBarProps {
  currentRole: UserRole;
  username: string;
  onRoleChange?: (role: UserRole) => void;
}

const roles: UserRole[] = ['super_admin', 'system_admin', 'admin', 'teacher', 'guardian'];
const KINDERGARTEN_ID = '1'; // 임시 유치원 ID

export function TopBar({ currentRole, username, onRoleChange }: TopBarProps) {
  const router = useRouter();
  const { user, logout } = useAuth();

  // 💡 알림 상태 관리
  const [notifications, setNotifications] = useState<any[]>([]); // Notification 타입 대체 가능
  const [unreadCount, setUnreadCount] = useState(0);

  // 💡 알림 데이터 불러오기 (주기적 폴링)
  useEffect(() => {
    if (!user) return;

    const fetchNotifications = async () => {
      try {
        const response = await apiClient.get(`/v1/kindergartens/${KINDERGARTEN_ID}/notifications`);
        const data = response.data.data || response.data;

        // 최신 알림순으로 정렬
        const sortedData = data.sort((a: any, b: any) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );

        setNotifications(sortedData);
        setUnreadCount(sortedData.filter((n: any) => !n.isRead).length);
      } catch (error) {
        console.error('알림을 불러오지 못했습니다:', error);
      }
    };

    fetchNotifications();
    // 30초마다 새 알림 확인
    const interval = setInterval(fetchNotifications, 30000);

    return () => clearInterval(interval);
  }, [user]);

  // 알림 모두 읽음 처리 핸들러 (UI 상의 예측 처리)
  const handleMarkAllAsRead = () => {
    setUnreadCount(0);
    setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
    // 실제 운영 시에는 백엔드 PATCH 요청을 보내서 읽음 처리를 해야 합니다.
    // apiClient.patch(`/v1/kindergartens/${KINDERGARTEN_ID}/notifications/read-all`);
  };

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  return (
      <div className="bg-purple-600 text-white px-6 py-3 flex items-center justify-between shadow-md relative z-10">
        <div className="flex items-center gap-4">
          <h1 className="text-lg font-semibold tracking-tight">햇살유치원 CCTV 관리</h1>
          <Badge className="bg-white/20 hover:bg-white/30 text-white border-white/40 font-normal">
            {roleLabels[currentRole]}
          </Badge>
        </div>

        <div className="flex items-center gap-2">

          {/* 💡 알림(Notification) 팝오버 드롭다운 추가 */}
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
                  notifications.map((notif) => (
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

          {/* 데모용 역할 전환 드롭다운 */}
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