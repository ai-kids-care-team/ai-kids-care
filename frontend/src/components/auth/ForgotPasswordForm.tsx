'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Shield, ArrowLeft } from 'lucide-react';

import {
  usePasswordResetRequestMutation,
  usePasswordResetVerifyMutation,
  getAuthApiErrorMessage,
} from '@/services/apis/auth.api';

type Step = 'request' | 'verify';

/**
 * 비밀번호 찾기 — 2단계(loginId 입력 → SMS 코드 확인 → resetToken 발급).
 *
 * 방지 열거(anti-enumeration) 핵심: `request` 는 계정 존재 여부와 무관하게 항상 200 +
 * `{ challengeId, expiresAt }` 를 반환한다 — 이 화면은 그 결과를 그대로 "계정이 존재하면
 * 인증번호가 발송되었습니다" 같은 중립 문구로만 보여주고, 존재/부재를 구분하는 어떤 표시도
 * 하지 않는다. verify 실패(오타/만료/더미 challenge/5회 초과) 도 백엔드가 이미 통일된 메시지
 * (`인증에 실패했습니다.`)를 주므로 그대로 노출해도 안전하다.
 *
 * 성공 시 resetToken 은 세션/로컬 스토리지에 두지 않고 `/reset-password` 로의 라우트
 * 쿼리 파라미터로만 1회성 전달한다(static export라 서버 세션이 없다).
 */
export function ForgotPasswordForm() {
  const router = useRouter();
  const [passwordResetRequest, { isLoading: isRequesting }] = usePasswordResetRequestMutation();
  const [passwordResetVerify, { isLoading: isVerifying }] = usePasswordResetVerifyMutation();

  const [step, setStep] = useState<Step>('request');
  const [loginId, setLoginId] = useState('');
  const [challengeId, setChallengeId] = useState('');
  const [code, setCode] = useState('');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');

  const handleRequestSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const trimmed = loginId.trim();
    if (!trimmed) {
      setError('로그인 ID를 입력해 주세요.');
      return;
    }

    try {
      const result = await passwordResetRequest({ loginId: trimmed }).unwrap();
      setChallengeId(result.challengeId);
      setNotice('입력하신 계정이 존재하면 등록된 휴대폰으로 인증번호가 발송되었습니다.');
      setStep('verify');
    } catch (err) {
      setError(getAuthApiErrorMessage(err, '요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'));
    }
  };

  const handleVerifySubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const trimmedCode = code.trim();
    if (!trimmedCode) {
      setError('인증번호를 입력해 주세요.');
      return;
    }

    try {
      const result = await passwordResetVerify({ challengeId, code: trimmedCode }).unwrap();
      const params = new URLSearchParams({ resetToken: result.resetToken });
      router.push(`/reset-password?${params.toString()}`);
    } catch (err) {
      setError(getAuthApiErrorMessage(err, '인증에 실패했습니다.'));
    }
  };

  const handleBackToRequest = () => {
    setStep('request');
    setCode('');
    setError('');
    setNotice('');
  };

  return (
    <div className="w-full max-w-md p-8 bg-white rounded-2xl shadow-2xl">
      <div className="text-center mb-8">
        <div className="inline-flex items-center justify-center w-16 h-16 bg-purple-100 rounded-2xl mb-4">
          <Shield className="w-8 h-8 text-purple-600" />
        </div>
        <h1 className="text-2xl font-bold text-gray-900 mb-2">비밀번호 찾기</h1>
        <p className="text-sm text-gray-600">
          {step === 'request'
            ? '가입 시 사용한 로그인 ID를 입력해 주세요.'
            : '휴대폰으로 발송된 인증번호를 입력해 주세요.'}
        </p>
      </div>

      {step === 'request' ? (
        <form onSubmit={handleRequestSubmit} className="space-y-4">
          <div>
            <label htmlFor="reset-login-id" className="block text-sm font-medium text-gray-700 mb-1">
              로그인 ID
            </label>
            <input
              id="reset-login-id"
              type="text"
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-600 focus:border-transparent outline-none transition-all"
              placeholder="아이디를 입력하세요"
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              lang="en"
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
            disabled={isRequesting}
          >
            {isRequesting ? '전송 중...' : '인증번호 받기'}
          </button>
        </form>
      ) : (
        <form onSubmit={handleVerifySubmit} className="space-y-4">
          {notice && (
            <div className="bg-purple-50 border border-purple-200 text-purple-800 px-4 py-3 rounded-lg text-sm">
              {notice}
            </div>
          )}

          <div>
            <label htmlFor="reset-code" className="block text-sm font-medium text-gray-700 mb-1">
              인증번호
            </label>
            <input
              id="reset-code"
              type="text"
              inputMode="numeric"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-600 focus:border-transparent outline-none transition-all"
              placeholder="6자리 숫자"
              autoComplete="one-time-code"
              maxLength={6}
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
            disabled={isVerifying}
          >
            {isVerifying ? '확인 중...' : '인증하기'}
          </button>

          <button
            type="button"
            onClick={handleBackToRequest}
            className="w-full text-sm text-gray-500 hover:text-purple-600 transition-colors"
          >
            로그인 ID를 다시 입력할게요
          </button>
        </form>
      )}

      <div className="mt-6 text-center">
        <Link href="/" className="inline-flex items-center text-sm text-gray-600 hover:text-purple-600 font-medium transition-colors">
          <ArrowLeft className="w-4 h-4 mr-1" />
          로그인으로 돌아가기
        </Link>
      </div>
    </div>
  );
}
