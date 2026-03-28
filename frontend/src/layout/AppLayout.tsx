'use client';

import { TopBar } from '@/layout/TopBar';
import { useAppSelector, useAppDispatch } from '@/store/hook';
import { setCredentials, logout } from '@/store/slices/userSlice';
import { usePathname } from 'next/navigation';
import { useLayoutEffect } from 'react';
import type { UserRole } from '@/types/user-role';
import { Toaster } from '@/components/shared/ui/sonner';

export function AppLayout({ children }: { children: React.ReactNode }) {

    const dispatch = useAppDispatch();
    const { user } = useAppSelector((state) => state.user);
    const pathname = usePathname();

    const hiddenTopBarRoutes = ['/forgot-password', '/reset-password'];
    const shouldShowTopBar = !hiddenTopBarRoutes.includes(pathname);
    const contentOverflowClass = 'overflow-auto';
    /** localStorage는 클라이언트 전용이라 첫 렌더에서 읽으면 SSR과 HTML이 달라 하이드레이션 오류가 난다. 세션 표시는 Redux만 사용하고, 아래 useLayoutEffect에서 스토어를 복구한다. */
    const sessionUser = user;
    const hasSession = Boolean(sessionUser);
    const currentRole: UserRole = sessionUser?.role ?? 'GUARDIAN';
    const username = sessionUser?.name ?? sessionUser?.username ?? '게스트';
    /** 로그인 세션이 없을 때만 ALL — `user`만 비고 localStorage에 유저가 있으면 역할 그대로 */
    const menuRoleType = hasSession ? sessionUser!.role : 'ANONYMOUS';

    /** `useEffect`보다 먼저 실행되어 첫 페인트 전에 Redux에 세션을 복구해, 메뉴 API가 ALL→역할로 두 번 호출되는 것을 막는다. */
    useLayoutEffect(() => {
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