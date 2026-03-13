'use client';

import { Sidebar } from '@/layout/Sidebar';
import { useAppSelector } from '@/store/hook';
import type { UserRole } from '@/types/anomaly';

export default function DashboardLayout({ children,}: {
    children: React.ReactNode;
}) {

    const { user } = useAppSelector((state) => state.user);
    const currentRole: UserRole = (user?.role ?? 'guardian') as UserRole;
    const currentUserName = user?.name ?? '게스트';

    return (
        <div className="flex h-full">
            
            <Sidebar
                currentRole={currentRole}
                userName={currentUserName}
                cameraStats={{ total: 0, online: 0, offline: 0 }}
                onCategoryFilter={() => {}}
                currentCategory="all"
            />

            <main className="flex-1 overflow-auto">
                {children}
            </main>

        </div>
    );
}