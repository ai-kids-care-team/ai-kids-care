'use client';

import { TopBar } from '@/layout/TopBar';
import { useAppSelector, useAppDispatch } from '@/store/hook';
import { setCredentials, logout } from '@/store/slices/userSlice';
import { usePathname } from 'next/navigation';
import { useEffect } from 'react';
import type { UserRole } from '@/types/user-role';
import { Toaster } from '@/components/shared/ui/sonner';

export function AppLayout({ children }: { children: React.ReactNode }) {

    const dispatch = useAppDispatch();
    const { user } = useAppSelector((state) => state.user);
    const pathname = usePathname();

    const hiddenTopBarRoutes = ['/forgot-password', '/reset-password'];
    const shouldShowTopBar = !hiddenTopBarRoutes.includes(pathname);
    const noScrollRoutes = ['/dashboard', '/detectionEvents'];
    const contentOverflowClass = noScrollRoutes.includes(pathname) ? 'overflow-hidden' : 'overflow-auto';
    const currentRole: UserRole = user?.role ?? 'GUARDIAN';
    const username = user?.name ?? user?.username ?? '게스트';

    useEffect(() => {
        if (typeof window === 'undefined' || user) return;

        try {
            const storedUser = localStorage.getItem('user');
            const storedToken = localStorage.getItem('accessToken') ?? localStorage.getItem('token');

            if (storedUser && storedToken) {
                const parsedUser = JSON.parse(storedUser);
                const user = parsedUser;
                dispatch(setCredentials({ user, token: storedToken }));
                return;
            }

            // 토큰만 남아 있는 비정상 상태는 정리해서 "로그인/권한" UI 불일치를 막는다.
            if (!storedUser && storedToken) {
                localStorage.removeItem('accessToken');
                localStorage.removeItem('token');
                localStorage.removeItem('refreshToken');
                dispatch(logout());
            }
        } catch {
            // 손상된 로컬스토리지 값은 무시하고 게스트 상태를 유지한다.
        }
    }, [dispatch, user]);

    return (
        <div className="h-screen flex flex-col">

            {shouldShowTopBar && (
                <TopBar
                    currentRole={currentRole}
                    username={username}
                />
            )}

            <div id="app-scroll-container" className={`flex-1 ${contentOverflowClass}`}>
                {children}
            </div>

            <Toaster position="top-right" richColors />
        </div>
    );
}