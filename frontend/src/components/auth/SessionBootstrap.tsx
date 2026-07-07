'use client';

import { useEffect } from 'react';
import { useSessionQuery } from '@/services/apis/auth.api';
import { getUnreadCount } from '@/services/apis/notifications.api';
import { useAppDispatch } from '@/store/hook';
import { logout, setCredentials, setUnreadNotificationCount } from '@/store/slices/userSlice';
import { isUserRole } from '@/types/user-role';

export function SessionBootstrap() {
  const dispatch = useAppDispatch();
  const { data, isError } = useSessionQuery();

  useEffect(() => {
    if (data && isUserRole(data.effectiveRole)) {
      dispatch(setCredentials({
        id: String(data.userId),
        loginId: data.loginId,
        username: data.loginId,
        name: data.name,
        role: data.effectiveRole,
        scopeType: data.scopeType,
        scopeId: data.scopeId,
        kindergartenId:
          data.scopeType === 'KINDERGARTEN' ? data.scopeId : undefined,
      }));
      // wire-notification-read-state D5: 세션 확인(로그인/새로고침) 시 ambient 배지 값을 채운다.
      // 실패해도 세션 자체는 이미 확립됐으므로 비치명적 — 배지만 갱신 안 된 채로 남는다.
      void getUnreadCount()
        .then((count) => dispatch(setUnreadNotificationCount(count)))
        .catch(() => {
          /* non-fatal: badge just stays stale until next refresh */
        });
    } else if (isError) {
      dispatch(logout());
    }
  }, [data, dispatch, isError]);

  return null;
}
