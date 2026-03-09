'use client'

import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import type { UserRole } from '../types/anomaly';

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

const API_URL = process.env.NEXT_PUBLIC_API_URL !== undefined
  ? process.env.NEXT_PUBLIC_API_URL
  : 'http://localhost:8081';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    const savedUser = localStorage.getItem('cctv_user');
    const savedToken = localStorage.getItem('cctv_token');

    if (savedUser && savedToken) {
      try {
        const parsedUser = JSON.parse(savedUser);

        // [핵심 수정 1] 과거에 잘못 저장된 로컬 스토리지 권한 데이터 강제 복구
        const validRoles = ['super_admin', 'system_admin', 'admin', 'teacher', 'guardian'];
        if (!validRoles.includes(parsedUser.role)) {
            parsedUser.role = 'guardian'; // 잘못된 권한이면 기본값으로 덮어씀
        }

        setUser(parsedUser);
        setToken(savedToken);
      } catch (e) {
        localStorage.removeItem('cctv_user');
        localStorage.removeItem('cctv_token');
      }
    }
    setIsLoading(false);
  }, []);

  const login = async (username: string, password: string): Promise<boolean> => {
    setIsLoading(true);

    try {
      const response = await fetch(`${API_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ loginId: username, password }),
      });

      if (response.ok) {
        const data = await response.json();

        // 백엔드에서 넘어올 수 있는 모든 형태의 권한 키값 유연하게 추출
        const rawRole = data.user?.memberType || data.memberType || data.user?.role || data.role || 'guardian';

        // [핵심 수정 2] 프론트엔드의 정확한 규격에 맞게 예외 없이 강제 매핑
        let mappedRole: UserRole = 'guardian';
        const upperRole = String(rawRole).toUpperCase();

        if (upperRole.includes('TEACHER')) mappedRole = 'teacher';
        else if (upperRole.includes('KINDERGARTEN') || upperRole === 'ADMIN') mappedRole = 'admin';
        else if (upperRole.includes('SYSTEM')) mappedRole = 'system_admin';
        else if (upperRole.includes('PLATFORM') || upperRole.includes('SUPER')) mappedRole = 'super_admin';
        else mappedRole = 'guardian'; // 정의되지 않은 모든 값은 무조건 guardian 처리하여 에러 방어

        const loggedInUser: User = {
          id: data.user?.id || data.id || 'unknown',
          username: data.user?.loginId || data.loginId || username,
          name: data.user?.name || data.name || '사용자',
          role: mappedRole,
          email: data.user?.email || data.email,
        };

        const receivedToken = data.token || 'real-token';

        setUser(loggedInUser);
        setToken(receivedToken);

        localStorage.setItem('cctv_user', JSON.stringify(loggedInUser));
        localStorage.setItem('cctv_token', receivedToken);

        setIsLoading(false);
        return true;
      }

      setIsLoading(false);
      return false;
    } catch (error) {
      console.error('로그인 오류:', error);
      setIsLoading(false);
      return false;
    }
  };

  const logout = () => {
    setUser(null);
    setToken(null);
    localStorage.removeItem('cctv_user');
    localStorage.removeItem('cctv_token');
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


// 'use client'
//
// import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
// import type { UserRole } from '../types/anomaly';
//
// interface User {
//   id: string;
//   username: string;
//   name: string;
//   role: UserRole;
//   email?: string;
// }
//
// interface AuthContextType {
//   user: User | null;
//   token: string | null;
//   isAuthenticated: boolean;
//   isLoading: boolean;
//   login: (username: string, password: string) => Promise<boolean>;
//   logout: () => void;
//   switchRole: (role: UserRole) => void;
// }
//
// const AuthContext = createContext<AuthContextType | undefined>(undefined);
//
// const API_URL = process.env.NEXT_PUBLIC_API_URL !== undefined
//   ? process.env.NEXT_PUBLIC_API_URL
//   : 'http://localhost:8081';
//
// export function AuthProvider({ children }: { children: ReactNode }) {
//   const [user, setUser] = useState<User | null>(null);
//   const [token, setToken] = useState<string | null>(null);
//   const [isLoading, setIsLoading] = useState<boolean>(true);
//
//   useEffect(() => {
//     const savedUser = localStorage.getItem('cctv_user');
//     const savedToken = localStorage.getItem('cctv_token');
//
//     if (savedUser && savedToken) {
//       try {
//         setUser(JSON.parse(savedUser));
//         setToken(savedToken);
//       } catch (e) {
//         localStorage.removeItem('cctv_user');
//         localStorage.removeItem('cctv_token');
//       }
//     }
//     setIsLoading(false);
//   }, []);
//
//   const login = async (username: string, password: string): Promise<boolean> => {
//     setIsLoading(true);
//
//     try {
//       const response = await fetch(`${API_URL}/api/auth/login`, {
//         method: 'POST',
//         headers: { 'Content-Type': 'application/json' },
//         body: JSON.stringify({ loginId: username, password }),
//       });
//
//       if (response.ok) {
//         const data = await response.json();
//
//         // 백엔드 응답 구조에 맞춰 데이터 파싱
//         const loggedInUser: User = {
//           id: data.user?.id || data.id || 'unknown',
//           username: data.user?.loginId || data.loginId || username,
//           name: data.user?.name || data.name || '사용자',
//           role: data.user?.memberType || data.memberType || 'GUARDIAN',
//           email: data.user?.email || data.email,
//         };
//
//         const receivedToken = data.token || 'real-token';
//
//         setUser(loggedInUser);
//         setToken(receivedToken);
//
//         localStorage.setItem('cctv_user', JSON.stringify(loggedInUser));
//         localStorage.setItem('cctv_token', receivedToken);
//
//         setIsLoading(false);
//         return true;
//       }
//
//       setIsLoading(false);
//       return false;
//     } catch (error) {
//       console.error('로그인 오류:', error);
//       setIsLoading(false);
//       return false;
//     }
//   };
//
//   const logout = () => {
//     setUser(null);
//     setToken(null);
//     localStorage.removeItem('cctv_user');
//     localStorage.removeItem('cctv_token');
//   };
//
//   const switchRole = (role: UserRole) => {
//     if (user) {
//       const updatedUser = { ...user, role };
//       setUser(updatedUser);
//       localStorage.setItem('cctv_user', JSON.stringify(updatedUser));
//     }
//   };
//
//   return (
//     <AuthContext.Provider
//       value={{
//         user,
//         token,
//         isAuthenticated: !!user,
//         isLoading,
//         login,
//         logout,
//         switchRole,
//       }}
//     >
//       {children}
//     </AuthContext.Provider>
//   );
// }
//
// export function useAuth() {
//   const context = useContext(AuthContext);
//   if (context === undefined) {
//     throw new Error('useAuth must be used within an AuthProvider');
//   }
//   return context;
// }


// 'use client'
//
// import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
// import type { UserRole } from '../types/anomaly';
//
// interface User {
//   id: string;
//   username: string;
//   name: string;
//   role: UserRole;
//   email?: string;
// }
//
// // 💡 1. 여기에 token과 isLoading 타입을 추가했습니다!
// interface AuthContextType {
//   user: User | null;
//   token: string | null;
//   isAuthenticated: boolean;
//   isLoading: boolean;
//   login: (username: string, password: string) => Promise<boolean>;
//   logout: () => void;
//   switchRole: (role: UserRole) => void;
// }
//
// const AuthContext = createContext<AuthContextType | undefined>(undefined);
//
// const mockUsers: Record<string, { password: string; user: User }> = {
//   'super': {
//     password: 'admin123',
//     user: { id: '1', username: 'super', name: '최고관리자', role: 'super_admin', email: 'super@kindergarten.com' },
//   },
//   'system': {
//     password: 'admin123',
//     user: { id: '2', username: 'system', name: '정시스템관리자', role: 'system_admin', email: 'system@kindergarten.com' },
//   },
//   'admin': {
//     password: 'admin123',
//     user: { id: '3', username: 'admin', name: '김원장', role: 'admin', email: 'admin@kindergarten.com' },
//   },
//   'teacher': {
//     password: 'teacher123',
//     user: { id: '4', username: 'teacher', name: '이선생', role: 'teacher', email: 'teacher@kindergarten.com' },
//   },
//   'guardian': {
//     password: 'parent123',
//     user: { id: '5', username: 'guardian', name: '박학부모', role: 'guardian', email: 'parent@example.com' },
//   },
// };
//
// export function AuthProvider({ children }: { children: ReactNode }) {
//   const [user, setUser] = useState<User | null>(null);
//   // 💡 2. token과 isLoading 상태(State)를 추가했습니다!
//   const [token, setToken] = useState<string | null>(null);
//   const [isLoading, setIsLoading] = useState<boolean>(true); // 초기 로딩 상태는 true
//
//   useEffect(() => {
//     // 💡 3. 앱 실행 시 저장된 user와 token을 함께 불러옵니다.
//     const savedUser = localStorage.getItem('cctv_user');
//     const savedToken = localStorage.getItem('cctv_token');
//
//     if (savedUser && savedToken) {
//       try {
//         setUser(JSON.parse(savedUser));
//         setToken(savedToken);
//       } catch (e) {
//         localStorage.removeItem('cctv_user');
//         localStorage.removeItem('cctv_token');
//       }
//     }
//     // 데이터 로드가 끝나면 로딩 상태를 false로 변경
//     setIsLoading(false);
//   }, []);
//
//   const login = async (username: string, password: string): Promise<boolean> => {
//     setIsLoading(true);
//     await new Promise(resolve => setTimeout(resolve, 500));
//
//     const mockUser = mockUsers[username];
//     if (mockUser && mockUser.password === password) {
//       // 💡 4. 로그인 성공 시 가짜 토큰을 생성하고 저장합니다.
//       const mockToken = `mock-token-${username}-${Date.now()}`;
//
//       setUser(mockUser.user);
//       setToken(mockToken);
//
//       localStorage.setItem('cctv_user', JSON.stringify(mockUser.user));
//       localStorage.setItem('cctv_token', mockToken);
//
//       setIsLoading(false);
//       return true;
//     }
//
//     setIsLoading(false);
//     return false;
//   };
//
//   const logout = () => {
//     setUser(null);
//     setToken(null); // 로그아웃 시 토큰도 비워줍니다.
//     localStorage.removeItem('cctv_user');
//     localStorage.removeItem('cctv_token');
//   };
//
//   const switchRole = (role: UserRole) => {
//     if (user) {
//       const updatedUser = { ...user, role };
//       setUser(updatedUser);
//       localStorage.setItem('cctv_user', JSON.stringify(updatedUser));
//     }
//   };
//
//   return (
//     <AuthContext.Provider
//       // 💡 5. 컨텍스트를 통해 token과 isLoading도 하위 컴포넌트로 전달합니다.
//       value={{
//         user,
//         token,
//         isAuthenticated: !!user,
//         isLoading,
//         login,
//         logout,
//         switchRole,
//       }}
//     >
//       {children}
//     </AuthContext.Provider>
//   );
// }
//
// export function useAuth() {
//   const context = useContext(AuthContext);
//   if (context === undefined) {
//     throw new Error('useAuth must be used within an AuthProvider');
//   }
//   return context;
// }

// 'use client'
//
// import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
//
//
// import type { UserRole } from '../types/anomaly';
//
// interface User {
//   id: string;
//   username: string;
//   name: string;
//   role: UserRole;
//   email?: string;
// }
//
// interface AuthContextType {
//   user: User | null;
//   isAuthenticated: boolean;
//   login: (username: string, password: string) => Promise<boolean>;
//   logout: () => void;
//   switchRole: (role: UserRole) => void;
// }
//
// const AuthContext = createContext<AuthContextType | undefined>(undefined);
//
// const mockUsers: Record<string, { password: string; user: User }> = {
//   'super': {
//     password: 'admin123',
//     user: {
//       id: '1',
//       username: 'super',
//       name: '최고관리자',
//       role: 'super_admin',
//       email: 'super@kindergarten.com',
//     },
//   },
//   'system': {
//     password: 'admin123',
//     user: {
//       id: '2',
//       username: 'system',
//       name: '정시스템관리자',
//       role: 'system_admin',
//       email: 'system@kindergarten.com',
//     },
//   },
//   'admin': {
//     password: 'admin123',
//     user: {
//       id: '3',
//       username: 'admin',
//       name: '김원장',
//       role: 'admin',
//       email: 'admin@kindergarten.com',
//     },
//   },
//   'teacher': {
//     password: 'teacher123',
//     user: {
//       id: '4',
//       username: 'teacher',
//       name: '이선생',
//       role: 'teacher',
//       email: 'teacher@kindergarten.com',
//     },
//   },
//   'guardian': {
//     password: 'parent123',
//     user: {
//       id: '5',
//       username: 'guardian',
//       name: '박학부모',
//       role: 'guardian',
//       email: 'parent@example.com',
//     },
//   },
// };
//
// export function AuthProvider({ children }: { children: ReactNode }) {
//   const [user, setUser] = useState<User | null>(null);
//
//   useEffect(() => {
//     const savedUser = localStorage.getItem('cctv_user');
//     if (savedUser) {
//       try {
//         setUser(JSON.parse(savedUser));
//       } catch (e) {
//         localStorage.removeItem('cctv_user');
//       }
//     }
//   }, []);
//
//   const login = async (username: string, password: string): Promise<boolean> => {
//     await new Promise(resolve => setTimeout(resolve, 500));
//
//     const mockUser = mockUsers[username];
//     if (mockUser && mockUser.password === password) {
//       setUser(mockUser.user);
//       localStorage.setItem('cctv_user', JSON.stringify(mockUser.user));
//       return true;
//     }
//     return false;
//   };
//
//   const logout = () => {
//     setUser(null);
//     localStorage.removeItem('cctv_user');
//   };
//
//   const switchRole = (role: UserRole) => {
//     if (user) {
//       const updatedUser = { ...user, role };
//       setUser(updatedUser);
//       localStorage.setItem('cctv_user', JSON.stringify(updatedUser));
//     }
//   };
//
//   return (
//     <AuthContext.Provider
//       value={{
//         user,
//         isAuthenticated: !!user,
//         login,
//         logout,
//         switchRole,
//       }}
//     >
//       {children}
//     </AuthContext.Provider>
//   );
// }
//
// export function useAuth() {
//   const context = useContext(AuthContext);
//   if (context === undefined) {
//     throw new Error('useAuth must be used within an AuthProvider');
//   }
//   return context;
// }
