'use client';

import Link from 'next/link';
import { Shield, ArrowLeft } from 'lucide-react';

export function ForgotPasswordForm() {
  return (
    <div className="w-full max-w-md p-8 bg-white rounded-2xl shadow-2xl">
      <div className="text-center mb-8">
        <div className="inline-flex items-center justify-center w-16 h-16 bg-purple-100 rounded-2xl mb-4">
          <Shield className="w-8 h-8 text-purple-600" />
        </div>
        <h1 className="text-2xl font-bold text-gray-900 mb-2">비밀번호 찾기</h1>
        <p className="text-sm text-gray-600">비밀번호 재설정 기능은 아직 제공되지 않습니다.</p>
      </div>

      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
        인증코드, 일회용 토큰, 요청 제한을 포함한 전용 흐름이 구현된 뒤 다시 제공됩니다.
      </div>

      <div className="mt-6 text-center">
        <Link href="/" className="inline-flex items-center text-sm text-gray-600 hover:text-purple-600 font-medium transition-colors">
          <ArrowLeft className="w-4 h-4 mr-1" />
          로그인으로 돌아가기
        </Link>
      </div>
    </div>
  );
}
