'use client';

import { Eye, EyeOff, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAppDispatch } from '@/store/hook';
import { setCredentials } from '@/store/slices/userSlice';
import { useLoginMutation } from '@/services/apis/auth.api';
import type { UserRole } from '@/types/anomaly';

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const mapBackendRoleToFrontendRole = (role: string): UserRole => {
  const normalized = String(role ?? '').trim().toUpperCase();

  if (normalized === 'SUPERADMIN' || normalized === 'SUPER_ADMIN') return 'super_admin';
  if (normalized === 'PLATFORM_IT_ADMIN' || normalized === 'SYSTEM_ADMIN') return 'system_admin';
  if (normalized === 'KINDERGARTEN_ADMIN' || normalized === 'ADMIN') return 'admin';
  if (normalized === 'TEACHER') return 'teacher';
  return 'guardian';
};

export function LoginModal({ isOpen, onClose }: LoginModalProps) {
  const dispatch = useAppDispatch();
  const [loginApi, { isLoading }] = useLoginMutation();
  const [formData, setFormData] = useState({
    loginId: '',
    password: '',
  });
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const resetForm = () => {
    setFormData({ loginId: '', password: '' });
    setError('');
    setShowPassword(false);
  };

  const handleModalClose = () => {
    resetForm();
    onClose();
  };

  useEffect(() => {
    if (!isOpen) {
      resetForm();
    }
  }, [isOpen]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    try {
      const response = await loginApi({
        loginId: formData.loginId,
        password: formData.password,
      }).unwrap();

      const { loginId: responseLoginId, role, token, name } = response;

      const user = {
        id: responseLoginId,
        username: responseLoginId,
        name: name || responseLoginId,
        role: mapBackendRoleToFrontendRole(role),
      };

      dispatch(setCredentials({ user, token }));
      handleModalClose();
    } catch (err: any) {
      setError(err?.data?.message || '아이디 또는 비밀번호가 올바르지 않습니다.');
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/30 backdrop-blur-sm" onClick={handleModalClose} />

      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-md p-8 animate-in fade-in zoom-in duration-200">
        <button
          onClick={handleModalClose}
          className="absolute top-4 right-4 p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-full transition-colors"
          aria-label="로그인 모달 닫기"
        >
          <X className="w-6 h-6" />
        </button>

        <div className="text-center mb-8">
          <h2 className="text-3xl mb-2">로그인</h2>
          <p className="text-gray-600">AI Kids Care에 오신 것을 환영합니다</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-sm mb-2">로그인 ID</label>
            <input
              type="text"
              name="loginId"
              value={formData.loginId}
              onChange={handleInputChange}
              required
              className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="아이디를 입력하세요"
            />
          </div>

          <div>
            <label className="block text-sm mb-2">비밀번호</label>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                name="password"
                value={formData.password}
                onChange={handleInputChange}
                required
                className="w-full px-4 py-3 pr-11 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="비밀번호를 입력하세요"
              />
              <button
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-700"
                aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
              >
                {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
              </button>
            </div>
          </div>

          <div className="flex items-center justify-between text-sm">
            <label className="flex items-center gap-2 cursor-pointer">
              <input type="checkbox" className="rounded" />
              <span className="text-gray-600">로그인 상태 유지</span>
            </label>
            <Link href="/forgot-password" className="text-blue-600 hover:underline">
              비밀번호 찾기
            </Link>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            {isLoading ? '로그인 중...' : '로그인'}
          </button>

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
              {error}
            </div>
          )}
        </form>

        <div className="relative my-6">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-gray-200"></div>
          </div>
          <div className="relative flex justify-center text-sm">
            <span className="px-4 bg-white text-gray-500">또는</span>
          </div>
        </div>

        <div className="text-center">
          <p className="text-gray-600 text-sm mb-4">아직 계정이 없으신가요?</p>
          <Link
            href="/signup"
            onClick={handleModalClose}
            className="inline-block w-full px-6 py-3 border-2 border-blue-600 text-blue-600 rounded-lg hover:bg-blue-50 transition-colors"
          >
            회원가입하기
          </Link>
        </div>
      </div>
    </div>
  );
}
