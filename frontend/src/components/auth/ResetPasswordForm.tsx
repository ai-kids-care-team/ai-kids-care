'use client';

import Link from 'next/link';
import { KeyRound, ArrowLeft } from 'lucide-react';

export function ResetPasswordForm() {
  return (
    <div className="w-full max-w-md p-8 bg-white rounded-2xl shadow-2xl">
      <div className="text-center mb-8">
        <div className="inline-flex items-center justify-center w-16 h-16 bg-purple-100 rounded-2xl mb-4">
          <KeyRound className="w-8 h-8 text-purple-600" />
        </div>
        <h1 className="text-2xl font-bold text-gray-900 mb-2">새 비밀번호 설정</h1>
        <p className="text-sm text-gray-600">비밀번호 재설정 기능은 아직 제공되지 않습니다.</p>
      </div>

      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
        재설정 토큰 검증과 안전한 비밀번호 변경 command가 구현된 뒤 다시 제공됩니다.
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
