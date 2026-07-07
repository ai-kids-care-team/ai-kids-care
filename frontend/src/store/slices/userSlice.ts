import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import type { UserRole } from '@/types/user-role';

export type { UserRole };

export interface User {
  id: string;
  loginId : string;
  name?: string;
  username: string; // 로그인 ID
  role: UserRole;
  email?: string;
  kindergartenId?: number;
  scopeType: 'PLATFORM' | 'KINDERGARTEN';
  scopeId?: number;
}

export interface UserState {
  user: User | null;
  isAuthenticated: boolean;
  sessionChecked: boolean;
  /**
   * wire-notification-read-state D5: 본인의 station 미읽음 알림 개수(ambient badge).
   * `SessionBootstrap` 이 세션 확인 시 `GET /notifications/unread-count` 로 채우고,
   * 알림함에서 낱개 읽음 처리할 때 낙관적으로 감소시킨다.
   */
  unreadNotificationCount: number;
}

const initialState: UserState = {
  user: null,
  isAuthenticated: false,
  sessionChecked: false,
  unreadNotificationCount: 0,
};

const userSlice = createSlice({
  name: 'user',
  initialState,
  reducers: {
    setCredentials: (state, action: PayloadAction<User>) => {
      state.user = action.payload;
      state.isAuthenticated = true;
      state.sessionChecked = true;
    },
    // 로그아웃 시 호출되어 상태 초기화
    logout: (state) => {
      state.user = null;
      state.isAuthenticated = false;
      state.sessionChecked = true;
      state.unreadNotificationCount = 0;
    },
    /** 세션 시작/갱신 시 서버 값으로 전체 치환 */
    setUnreadNotificationCount: (state, action: PayloadAction<number>) => {
      state.unreadNotificationCount = Math.max(0, action.payload);
    },
    /** 알림 1건 읽음 처리 시 낙관적 감소(0 아래로 내려가지 않음) */
    decrementUnreadNotificationCount: (state) => {
      state.unreadNotificationCount = Math.max(0, state.unreadNotificationCount - 1);
    },
  },
});

export const {
  setCredentials,
  logout,
  setUnreadNotificationCount,
  decrementUnreadNotificationCount,
} = userSlice.actions;
export default userSlice.reducer;
