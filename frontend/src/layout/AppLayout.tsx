'use client';

import { TopBar } from '@/layout/TopBar';
import { useAppSelector, useAppDispatch } from '@/store/hook';
import { switchRole } from '@/store/slices/userSlice';
import { usePathname } from 'next/navigation';
import type { UserRole } from '@/types/anomaly';

export function AppLayout({ children }: { children: React.ReactNode }) {

    const dispatch = useAppDispatch();
    const { user } = useAppSelector((state) => state.user);
    const pathname = usePathname();

    const hiddenTopBarRoutes = ['/login', '/forgot-password', '/reset-password'];
    const shouldShowTopBar = !hiddenTopBarRoutes.includes(pathname);
    const noScrollRoutes = ['/dashboard', '/detectionEvents'];
    const contentOverflowClass = noScrollRoutes.includes(pathname) ? 'overflow-hidden' : 'overflow-auto';
    const currentRole: UserRole = (user?.role ?? 'guardian') as UserRole;
    const username = user?.name ?? user?.username ?? '게스트';

    return (
        <div className="h-screen flex flex-col">

            {shouldShowTopBar && (
                <TopBar
                    currentRole={currentRole}
                    username={username}
                    onRoleChange={user ? (r) => dispatch(switchRole(r)) : undefined}
                />
            )}

            <div className={`flex-1 ${contentOverflowClass}`}>
                {children}
            </div>

        </div>
    );
}