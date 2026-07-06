import { Suspense } from 'react';
import { ResetPasswordForm } from '@/components/auth/ResetPasswordForm';

export default function ResetPasswordPage() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-600 to-indigo-700 flex items-center justify-center p-4">
      <Suspense fallback={<div className="text-white">불러오는 중입니다.</div>}>
        <ResetPasswordForm />
      </Suspense>
    </div>
  );
}