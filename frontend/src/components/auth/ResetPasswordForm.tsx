'use client';

import { useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { KeyRound, ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';

import { usePasswordResetConfirmMutation, getAuthApiErrorMessage } from '@/services/apis/auth.api';

/**
 * 새 비밀번호 설정 — 비밀번호 찾기 3단계(마지막).
 *
 * `resetToken` 은 `ForgotPasswordForm` 의 verify 성공 후 `/reset-password?resetToken=...`
 * 라우트 쿼리로만 1회성 전달된다(localStorage/sessionStorage 에 두지 않는다). 이 페이지에
 * 직접 진입해 토큰이 없으면 비밀번호 찾기 처음으로 안내한다.
 */
export function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const resetToken = searchParams.get('resetToken') ?? '';
  const [passwordResetConfirm, { isLoading }] = usePasswordResetConfirmMutation();

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');

  if (!resetToken) {
    return (
      <div className="w-full max-w-md p-8 bg-white rounded-2xl shadow-2xl">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-purple-100 rounded-2xl mb-4">
            <KeyRound className="w-8 h-8 text-purple-600" />
          </div>
          <h1 className="text-2xl font-bold text-gray-900 mb-2">새 비밀번호 설정</h1>
        </div>

        <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
          유효한 재설정 요청을 찾을 수 없습니다. 비밀번호 찾기를 처음부터 다시 진행해 주세요.
        </div>

        <div className="mt-6 text-center">
          <Link href="/forgot-password" className="inline-flex items-center text-sm text-gray-600 hover:text-purple-600 font-medium transition-colors">
            <ArrowLeft className="w-4 h-4 mr-1" />
            비밀번호 찾기로 이동
          </Link>
        </div>
      </div>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (newPassword !== confirmPassword) {
      setError('새 비밀번호가 일치하지 않습니다.');
      return;
    }

    try {
      await passwordResetConfirm({ resetToken, newPassword }).unwrap();
      toast.success('비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.');
      router.push('/');
    } catch (err) {
      setError(getAuthApiErrorMessage(err, '비밀번호 재설정에 실패했습니다.'));
    }
  };

  return (
    <div className="w-full max-w-md p-8 bg-white rounded-2xl shadow-2xl">
      <div className="text-center mb-8">
        <div className="inline-flex items-center justify-center w-16 h-16 bg-purple-100 rounded-2xl mb-4">
          <KeyRound className="w-8 h-8 text-purple-600" />
        </div>
        <h1 className="text-2xl font-bold text-gray-900 mb-2">새 비밀번호 설정</h1>
        <p className="text-sm text-gray-600">새로 사용할 비밀번호를 입력해 주세요.</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="new-password" className="block text-sm font-medium text-gray-700 mb-1">
            새 비밀번호
          </label>
          <input
            id="new-password"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-600 focus:border-transparent outline-none transition-all"
            placeholder="새 비밀번호"
            autoComplete="new-password"
            minLength={8}
            required
          />
          <p className="mt-1 text-xs text-gray-500">영문·숫자를 포함해 8자 이상, 동일 문자만으로 구성할 수 없습니다.</p>
        </div>

        <div>
          <label htmlFor="new-password-confirm" className="block text-sm font-medium text-gray-700 mb-1">
            새 비밀번호 확인
          </label>
          <input
            id="new-password-confirm"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-600 focus:border-transparent outline-none transition-all"
            placeholder="새 비밀번호 확인"
            autoComplete="new-password"
            required
          />
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
          {isLoading ? '변경 중...' : '비밀번호 변경'}
        </button>
      </form>

      <div className="mt-6 text-center">
        <Link href="/" className="inline-flex items-center text-sm text-gray-600 hover:text-purple-600 font-medium transition-colors">
          <ArrowLeft className="w-4 h-4 mr-1" />
          로그인으로 돌아가기
        </Link>
      </div>
    </div>
  );
}
