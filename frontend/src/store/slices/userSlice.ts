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
}

const initialState: UserState = {
  user: null,
  isAuthenticated: false,
  sessionChecked: false,
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
    },
  },
});

export const { setCredentials, logout } = userSlice.actions;
export default userSlice.reducer;
