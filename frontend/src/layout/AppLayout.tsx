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

    const hiddenTopBarRoutes = ['/login', '/signup', '/forgot-password', '/reset-password'];
    const shouldShowTopBar = !hiddenTopBarRoutes.includes(pathname);
    const currentRole: UserRole = (user?.role ?? 'guardian') as UserRole;
    const username = user?.name ?? '게스트';

    return (
        <div className="h-screen flex flex-col">

            {shouldShowTopBar && (
                <TopBar
                    currentRole={currentRole}
                    username={username}
                    onRoleChange={(r) => dispatch(switchRole(r))}
                />
            )}

            <div className="flex-1 overflow-hidden">
                {children}
            </div>

        </div>
    );
}