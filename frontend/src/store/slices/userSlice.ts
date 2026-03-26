import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import type { UserRole } from '@/types/user-role';

export type { UserRole };

export interface User {
  id: string;
  loginId : string;
  name: string;
  username: string; // 로그인 ID
  role: UserRole;
  email?: string;
}

export interface UserState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
}

const initialState: UserState = {
  user: null,
  token: null,
  isAuthenticated: false,
};

const userSlice = createSlice({
  name: 'user',
  initialState,
  reducers: {
    // 로그인 성공 시 호출되어 유저 정보와 토큰을 저장
    setCredentials: (
      state,
      action: PayloadAction<{ user: User; token: string }>
    ) => {
      state.user = action.payload.user;
      state.token = action.payload.token;
      state.isAuthenticated = true;
    },
    // 로그아웃 시 호출되어 상태 초기화
    logout: (state) => {
      state.user = null;
      state.token = null;
      state.isAuthenticated = false;
    },
    // 데모용 역할 전환 기능
    switchRole: (state, action: PayloadAction<UserRole>) => {
      if (state.user) {
        state.user.role = action.payload;
      }
    },
  },
});

export const { setCredentials, logout, switchRole } = userSlice.actions;
export default userSlice.reducer;