'use client';

import { TopBar } from '@/layout/TopBar';
import { useAppSelector } from '@/store/hook';
import { usePathname } from 'next/navigation';
import type { UserRole } from '@/types/user-role';
import { Toaster } from '@/components/shared/ui/sonner';

export function AppLayout({ children }: { children: React.ReactNode }) {

    const { user } = useAppSelector((state) => state.user);
    const pathname = usePathname();

    const hiddenTopBarRoutes = ['/forgot-password', '/reset-password'];
    const shouldShowTopBar = !hiddenTopBarRoutes.includes(pathname);
    const contentOverflowClass = 'overflow-auto';
    const sessionUser = user;
    const hasSession = Boolean(sessionUser);
    const currentRole: UserRole = sessionUser?.role ?? 'GUARDIAN';
    /** 상단바 로그인·로그아웃 옆: 로그인 아이디 우선 (`name`이 빈 문자열이면 `??`만으로는 `username`으로 안 넘어가 비어 보임) */
    const username =
      sessionUser?.loginId?.trim() ||
      sessionUser?.username?.trim() ||
      sessionUser?.name?.trim() ||
      '게스트';
    const menuRoleType = hasSession ? sessionUser!.role : 'ANONYMOUS';

    return (
        <div className="flex h-screen min-h-0 flex-col">

            {shouldShowTopBar && (
                <header className="shrink-0">
                    <TopBar
                        currentRole={currentRole}
                        username={username}
                        menuRoleType={menuRoleType}
                        hasSession={hasSession}
                    />
                </header>
            )}

            <div
                id="app-scroll-container"
                className={`min-h-0 flex-1 ${contentOverflowClass}`}
            >
                {children}
            </div>

            <Toaster position="top-right" richColors />
        </div>
    );
}
