'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { KeyRound, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/shared/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/shared/ui/card';
import { useAppDispatch } from '@/store/hook';
import { logout } from '@/store/slices/userSlice';
import { clearCsrf } from '@/services/csrf';
import { useChangePasswordMutation, getAuthApiErrorMessage } from '@/services/apis/auth.api';

/**
 * 로그인 상태 자기 비밀번호 변경 카드(개인설정 모달 전용).
 *
 * 백엔드는 성공(204) 시 해당 사용자의 **전체 세션(현재 세션 포함)을 무효화**한다
 * (`api-contract.md` change-password 참고) — 따라서 성공 콜백에서 `user` reducer 를
 * 비우고 로그인 화면(홈)으로 보낸다. 새 비밀번호로 다시 로그인해야 한다는 안내를 띄운다.
 */
export function ChangePasswordCard({ onDone }: { onDone?: () => void }) {
  const router = useRouter();
  const dispatch = useAppDispatch();
  const [changePassword, { isLoading }] = useChangePasswordMutation();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (newPassword !== confirmPassword) {
      setError('새 비밀번호가 일치하지 않습니다.');
      return;
    }

    try {
      await changePassword({ currentPassword, newPassword }).unwrap();
      toast.success('비밀번호를 변경했습니다. 새 비밀번호로 다시 로그인해 주세요.');
      onDone?.();
      clearCsrf();
      dispatch(logout());
      router.push('/');
    } catch (err) {
      setError(getAuthApiErrorMessage(err, '비밀번호 변경에 실패했습니다.'));
    }
  };

  return (
    <Card className="w-full">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <KeyRound className="h-5 w-5 text-purple-600" />
          비밀번호 변경
        </CardTitle>
        <CardDescription>
          변경하면 이 기기를 포함한 모든 로그인 세션이 종료됩니다. 새 비밀번호로 다시 로그인해 주세요.
        </CardDescription>
      </CardHeader>

      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="space-y-1">
            <label className="block text-sm font-medium text-gray-700" htmlFor="current-password">
              현재 비밀번호
            </label>
            <input
              id="current-password"
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              autoComplete="current-password"
              required
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-500/30"
            />
          </div>

          <div className="space-y-1">
            <label className="block text-sm font-medium text-gray-700" htmlFor="new-password">
              새 비밀번호
            </label>
            <input
              id="new-password"
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password"
              required
              minLength={8}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-500/30"
            />
            <p className="text-xs text-gray-500">영문·숫자를 포함해 8자 이상, 동일 문자만으로 구성할 수 없습니다.</p>
          </div>

          <div className="space-y-1">
            <label className="block text-sm font-medium text-gray-700" htmlFor="new-password-confirm">
              새 비밀번호 확인
            </label>
            <input
              id="new-password-confirm"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              autoComplete="new-password"
              required
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-500/30"
            />
          </div>

          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </div>
          )}

          <Button type="submit" disabled={isLoading} className="w-full">
            {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}
            비밀번호 변경
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
