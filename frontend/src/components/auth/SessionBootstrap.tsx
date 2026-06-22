'use client';

import { useEffect } from 'react';
import { useSessionQuery } from '@/services/apis/auth.api';
import { useAppDispatch } from '@/store/hook';
import { logout, setCredentials } from '@/store/slices/userSlice';
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
    } else if (isError) {
      dispatch(logout());
    }
  }, [data, dispatch, isError]);

  return null;
}
