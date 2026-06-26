'use client';

import { Bell } from 'lucide-react';
import { NotificationReadVO, isNotificationUnread } from '@/services/apis/notifications.api';

type NotificationsListFormProps = {
  notifications: NotificationReadVO[];
  unreadCount: number;
  loading: boolean;
  error: string;
};

function formatDate(value: string): string {
  const date = new Date(value);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  const hh = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd} ${hh}:${min}`;
}

export function NotificationsListForm({
  notifications,
  unreadCount,
  loading,
  error,
}: NotificationsListFormProps) {
  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-3xl">
        <div className="rounded-2xl bg-white p-8 shadow-lg">
          {/* 헤더 */}
          <div className="mb-8 flex items-center justify-between border-b border-gray-200 pb-6">
            <div className="flex items-center gap-3">
              <div className="relative">
                <Bell className="h-7 w-7 text-[#006b52]" />
                {unreadCount > 0 && (
                  <span className="absolute -right-2 -top-2 flex h-5 w-5 items-center justify-center rounded-full bg-red-500 text-[10px] font-bold text-white">
                    {unreadCount > 99 ? '99+' : unreadCount}
                  </span>
                )}
              </div>
              <h2 className="text-3xl">알림 수신함</h2>
            </div>
          </div>

          {/* 에러 */}
          {error && (
            <p className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-600">{error}</p>
          )}

          {/* 목록 */}
          <div className="min-h-[400px] space-y-3">
            {loading ? (
              <p className="flex min-h-[400px] items-center justify-center text-center text-gray-500">
                알림을 불러오는 중입니다.
              </p>
            ) : notifications.length === 0 ? (
              <p className="flex min-h-[400px] items-center justify-center text-center text-gray-500">
                받은 알림이 없습니다.
              </p>
            ) : (
              notifications.map((n) => {
                const unread = isNotificationUnread(n);
                return (
                  <div
                    key={n.notificationId}
                    className={[
                      'rounded-lg border p-5 transition-all',
                      unread
                        ? 'border-emerald-300 bg-emerald-50'
                        : 'border-gray-200 bg-white',
                    ].join(' ')}
                  >
                    <div className="flex items-start gap-3">
                      {unread && (
                        <span className="mt-0.5 flex-shrink-0 rounded bg-red-500 px-2 py-0.5 text-xs font-semibold text-white">
                          NEW
                        </span>
                      )}
                      <div className="min-w-0 flex-1">
                        <p className="text-base font-medium text-slate-800">{n.title}</p>
                        <p className="mt-1 whitespace-pre-wrap text-sm text-slate-600">{n.body}</p>
                        <div className="mt-2 flex items-center gap-3 text-xs text-gray-400">
                          <span>{formatDate(n.createdAt)}</span>
                          <span
                            className={[
                              'rounded px-1.5 py-0.5 font-medium',
                              n.status === 'SENT'
                                ? 'bg-gray-100 text-gray-500'
                                : n.status === 'PENDING'
                                  ? 'bg-yellow-100 text-yellow-700'
                                  : 'bg-red-100 text-red-600',
                            ].join(' ')}
                          >
                            {n.status}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
