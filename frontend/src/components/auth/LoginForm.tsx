'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Eye, EyeOff, Shield } from 'lucide-react';

import { useAppDispatch } from '@/store/hook';
import { setCredentials } from '@/store/slices/userSlice';
import { useLoginMutation } from '../../services/apis/auth.api';
import type { UserRole } from '@/types/user-role';

const normalizeLoginId = (value: string) => value.replace(/[^A-Za-z0-9]/g, '');
const inferKindergartenIdFromUserId = (userId: number): number | undefined => {
  if (!Number.isFinite(userId) || userId <= 0) return undefined;
  if (userId >= 700) return 3;
  if (userId >= 400) return 2;
  if (userId >= 100) return 1;
  return undefined;
};

export function LoginForm() {
  const router = useRouter();
  const dispatch = useAppDispatch();
  const [loginApi, { isLoading }] = useLoginMutation();

  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    console.log('로그인 버튼 클릭됨!', loginId, password);

    try {
      // RTK Query를 통한 로그인 API 호출 (.unwrap()으로 에러 캐치)
      const response = await loginApi({ identifier: loginId, password }).unwrap();

      const responseLoginId = response?.loginId ?? loginId;
      const responseIdRaw = response?.id;
      const responseIdNum = Number(responseIdRaw);
      const responseUserId =
        Number.isFinite(responseIdNum) && responseIdNum > 0
          ? Math.trunc(responseIdNum)
          : undefined;
      const role = response?.role ?? 'GUARDIAN';
      const token = response?.accessToken ?? response?.token ?? '';
      const apiName =
        typeof response?.name === 'string' && response.name.trim() !== '' ? response.name.trim() : '';
      const refreshToken = response?.refreshToken ?? '';
      const displayName = response?.name ?? responseLoginId;

      const user = {
          id: String(responseUserId ?? responseLoginId),
          username: responseLoginId,
          loginId: responseLoginId,
          name: apiName,
          role: role as UserRole,
      };

      // 1. Redux 스토어에 유저 정보와 토큰 저장
      dispatch(setCredentials({ user, token }));

      // 💡 [추가된 부분] 2. 브라우저 LocalStorage에 백업 (새로고침 방어용)
      localStorage.setItem('user', JSON.stringify(user));
      if (token) {
        localStorage.setItem('token', token);
        localStorage.setItem('accessToken', token);
      }
      if (refreshToken) {
        localStorage.setItem('refreshToken', refreshToken);
      }
      // -------------------------------------------------------------

      router.push('/');

    } catch (err: any) {
      setError(err?.data?.message || '아이디 또는 비밀번호가 올바르지 않습니다.');
    }
  };

  return (
    <div className="w-full max-w-md p-8 bg-white rounded-2xl shadow-2xl">
      <div className="text-center mb-8">
        <div className="inline-flex items-center justify-center w-16 h-16 bg-purple-600 rounded-2xl mb-4">
          <Shield className="w-8 h-8 text-white" />
        </div>
        <h1 className="text-2xl font-bold text-gray-900 mb-2">햇살유치원</h1>
        <p className="text-sm text-gray-600">CCTV 통합 관리 시스템</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="loginId" className="block text-sm font-medium text-gray-700 mb-1">
            로그인 ID
          </label>
          <input
            id="loginId"
            type="text"
            value={loginId}
            onChange={(e) => setLoginId(normalizeLoginId(e.target.value))}
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-600 focus:border-transparent outline-none transition-all"
            placeholder="아이디를 입력하세요"
            inputMode="text"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            lang="en"
            required
          />
        </div>

        <div>
          <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
            비밀번호
          </label>
          <div className="relative">
            <input
              id="password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-600 focus:border-transparent outline-none transition-all pr-10"
              placeholder="비밀번호를 입력하세요"
              required
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-700"
            >
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
            {error}
          </div>
        )}

        <button
          type="submit"
          className="w-full bg-purple-600 hover:bg-purple-700 text-white font-medium py-2.5 rounded-lg transition-colors disabled:opacity-50"
          disabled={isLoading}
        >
          {isLoading ? '로그인 중...' : '로그인'}
        </button>
      </form>

        <div className="mt-4 text-center">
          <Link href="/signup" className="text-sm text-purple-600 hover:text-purple-800 font-medium transition-colors">
            아직 계정이 없으신가요? 회원가입 하러가기
          </Link>
        </div>
        <div className="mt-2 text-center">
          <Link href="/forgot-password" className="text-sm text-gray-500 hover:text-gray-700 font-medium transition-colors">
            비밀번호를 잊으셨나요?
          </Link>
        </div>

        <div className="mt-6 pt-6 border-t border-gray-200">
          <p className="text-xs text-gray-500 text-center mb-3">데모 계정</p>
          <div className="grid grid-cols-2 gap-2 text-xs">
            <div className="bg-purple-50 p-2 rounded border border-purple-200">
              <p className="font-medium text-purple-900">슈퍼관리자</p>
              <p className="text-purple-700">super / admin123</p>
            </div>
            <div className="bg-indigo-50 p-2 rounded border border-indigo-200">
              <p className="font-medium text-indigo-900">시스템관리자</p>
              <p className="text-indigo-700">system / admin123</p>
            </div>
            <div className="bg-blue-50 p-2 rounded border border-blue-200">
              <p className="font-medium text-blue-900">원장(관리자)</p>
              <p className="text-blue-700">admin / admin123</p>
            </div>
            <div className="bg-green-50 p-2 rounded border border-green-200">
              <p className="font-medium text-green-900">선생님</p>
              <p className="text-green-700">teacher / teacher123</p>
            </div>
            <div className="bg-orange-50 p-2 rounded border border-orange-200 col-span-2">
              <p className="font-medium text-orange-900">학부모</p>
              <p className="text-orange-700">guardian / parent123</p>
            </div>
          </div>
        </div>
    </div>
  );
}