'use client'

import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import type { UserRole } from '@/types/user-role';
import { USER_ROLES } from '@/types/user-role';
import { apiClient } from '@/services/apis/apiClient'; // 👈 새로 만든 API 클라이언트 불러오기

interface User {
  id: string;
  username: string;
  name: string;
  role: UserRole;
  email?: string;
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
  switchRole: (role: UserRole) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    // 앱 실행 시 저장된 정보 불러오기
    const savedUser = localStorage.getItem('cctv_user');
    const savedToken = localStorage.getItem('accessToken'); // apiClient와 맞추기 위해 키 변경

    if (savedUser && savedToken) {
      try {
        const parsedUser = JSON.parse(savedUser);

        // [핵심 수정 1 유지] 과거에 잘못 저장된 로컬 스토리지 권한 데이터 강제 복구
        const validRoles = new Set<string>(USER_ROLES);
        if (!validRoles.has(parsedUser.role)) {
          parsedUser.role = 'GUARDIAN';
        }

        setUser(parsedUser);
        setToken(savedToken);

        // (선택) 여기서 apiClient.get('/v1/users/me')를 호출하여 서버 쪽에 토큰이 아직 유효한지
        // 한 번 더 검증하는 로직을 추가할 수도 있습니다.
      } catch (e) {
        localStorage.removeItem('cctv_user');
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
      }
    }
    setIsLoading(false);
  }, []);

  const login = async (username: string, password: string): Promise<boolean> => {
    setIsLoading(true);

    try {
      // 👈 fetch 대신 apiClient 사용 (baseUrl은 apiClient 내부에서 처리됨)
      const response = await apiClient.post('/auth/login', {
        identifier: username,
        password
      });

      // axios는 기본적으로 응답 데이터를 .data 안에 담아줍니다.
      const data = response.data;

      // 백엔드에서 넘어올 수 있는 모든 형태의 권한 키값 유연하게 추출
      const rawRole = data.user?.memberType || data.memberType || data.user?.role || data.role || 'GUARDIAN';
      const mappedRole = String(rawRole).toUpperCase() as UserRole;

      const loggedInUser: User = {
        id: data.user?.id || data.id || 'unknown',
        username: data.user?.loginId || data.loginId || username,
        name: data.user?.name || data.name || '사용자',
        role: mappedRole,
        email: data.user?.email || data.email,
      };

      // 백엔드 응답 스키마에 따라 토큰 변수명 매핑
      const receivedAccessToken = data.accessToken || data.token || 'real-token';
      const receivedRefreshToken = data.refreshToken || null;

      setUser(loggedInUser);
      setToken(receivedAccessToken);

      // 로컬 스토리지 저장 (apiClient가 읽을 수 있도록 accessToken, refreshToken으로 분리)
      localStorage.setItem('cctv_user', JSON.stringify(loggedInUser));
      localStorage.setItem('accessToken', receivedAccessToken);
      if (receivedRefreshToken) {
        localStorage.setItem('refreshToken', receivedRefreshToken);
      }

      setIsLoading(false);
      return true;

//     } catch (error) {
//       console.error('로그인 오류:', error);
//       setIsLoading(false);
//       return false;
//     }
    } catch (error: any) {
      console.error('로그인 오류:', error);
      setIsLoading(false);
      // 에러를 무시하지 않고 그대로 던져서 login/page.tsx가 잡게 함
      throw new Error(error.response?.data?.message || '로그인 처리 중 서버와 통신할 수 없습니다.');
    }
  };

  const logout = async () => {
    try {
      // 서버측 토큰 만료 처리 (에러가 나도 무시하고 클라이언트 데이터는 지움)
      await apiClient.post('/auth/logout');
    } catch (error) {
      console.error('로그아웃 API 호출 에러:', error);
    } finally {
      setUser(null);
      setToken(null);
      localStorage.removeItem('cctv_user');
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
    }
  };

  const switchRole = (role: UserRole) => {
    if (user) {
      const updatedUser = { ...user, role };
      setUser(updatedUser);
      localStorage.setItem('cctv_user', JSON.stringify(updatedUser));
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!user,
        isLoading,
        login,
        logout,
        switchRole,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}