'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Shield, Mail, ArrowLeft } from 'lucide-react';
import { useForgotPasswordMutation } from '../../services/apis/auth.api';

const getForgotPasswordErrorMessage = (err: any) => {
  if (err?.status === 'FETCH_ERROR') {
    return '백엔드 서버에 연결할 수 없습니다. 백엔드가 실행 중인지 확인해주세요.';
  }

  return err?.data?.error || err?.data?.message || '요청을 처리하는 중 오류가 발생했습니다. 이메일을 다시 확인해주세요.';
};

export function ForgotPasswordForm() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [isSuccess, setIsSuccess] = useState(false);

  const [forgotPasswordApi, { isLoading }] = useForgotPasswordMutation();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    try {
      await forgotPasswordApi({ email }).unwrap();
      setIsSuccess(true);
    } catch (err: any) {
      setError(getForgotPasswordErrorMessage(err));
    }
  };

  return (
    <div className="w-full max-w-md p-8 bg-white rounded-2xl shadow-2xl">
      <div className="text-center mb-8">
        <div className="inline-flex items-center justify-center w-16 h-16 bg-purple-100 rounded-2xl mb-4">
          <Shield className="w-8 h-8 text-purple-600" />
        </div>
        <h1 className="text-2xl font-bold text-gray-900 mb-2">비밀번호 찾기</h1>
        <p className="text-sm text-gray-600">가입 시 등록한 이메일을 입력해 주세요.</p>
      </div>

      {isSuccess ? (
        <div className="text-center">
          <div className="bg-emerald-50 text-emerald-700 p-4 rounded-lg mb-6">
            비밀번호 재설정 안내 메일(또는 인증코드)이 발송되었습니다. 이메일함을 확인해 주세요.
          </div>
          <Link href="/reset-password" className="block w-full bg-purple-600 hover:bg-purple-700 text-white font-medium py-2.5 rounded-lg transition-colors">
            인증코드 입력하기
          </Link>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">
              이메일 주소
            </label>
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-600 focus:border-transparent outline-none transition-all"
                placeholder="name@example.com"
                required
              />
            </div>
          </div>

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={isLoading || !email}
            className="w-full bg-purple-600 hover:bg-purple-700 text-white font-medium py-2.5 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isLoading ? '발송 중...' : '비밀번호 재설정 요청'}
          </button>
        </form>
      )}

      <div className="mt-6 text-center">
        <Link href="/login" className="inline-flex items-center text-sm text-gray-600 hover:text-purple-600 font-medium transition-colors">
          <ArrowLeft className="w-4 h-4 mr-1" />
          로그인으로 돌아가기
        </Link>
      </div>
    </div>
  );
}