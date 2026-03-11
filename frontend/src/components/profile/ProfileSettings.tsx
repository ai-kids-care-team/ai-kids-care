'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { User, Lock, Save, AlertCircle } from 'lucide-react';

import { useAppSelector, useAppDispatch } from '@/store/hook';
import { switchRole } from '@/store/slices/userSlice';
import { useChangePasswordMutation } from '@/services/apis/auth.api';
import { TopBar } from '@/layout/TopBar';

export function ProfileSettings() {
  const router = useRouter();
  const dispatch = useAppDispatch();

  // Redux에서 유저 상태 구독
  const { user, isAuthenticated } = useAppSelector((state) => state.user);
  const [changePasswordApi, { isLoading: isSubmitting }] = useChangePasswordMutation();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const [message, setMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null);

  // 인증되지 않은 사용자 접근 제어 (ProtectedRoute가 있다면 생략 가능)
  if (!isAuthenticated || !user) {
    router.replace('/auth/login');
    return null;
  }

  const handleRoleChange = (role: any) => {
    dispatch(switchRole(role));
  };

  const handlePasswordChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);

    if (newPassword !== confirmNewPassword) {
      setMessage({ type: 'error', text: '새 비밀번호가 일치하지 않습니다.' });
      return;
    }

    try {
      await changePasswordApi({ currentPassword, newPassword }).unwrap();
      setMessage({ type: 'success', text: '비밀번호가 성공적으로 변경되었습니다.' });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmNewPassword('');
    } catch (error) {
      setMessage({ type: 'error', text: '현재 비밀번호가 틀렸거나 변경에 실패했습니다.' });
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      <TopBar currentRole={user.role} username={user.name} onRoleChange={handleRoleChange} />

      <main className="flex-1 max-w-4xl w-full mx-auto p-6 md:p-10">
        <h1 className="text-2xl font-bold text-slate-900 mb-8">내 프로필 설정</h1>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {/* 기본 정보 표시 영역 */}
          <div className="md:col-span-1 space-y-4">
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200 text-center">
              <div className="w-20 h-20 bg-purple-100 text-purple-600 rounded-full flex items-center justify-center mx-auto mb-4">
                <User className="w-10 h-10" />
              </div>
              <h2 className="text-xl font-bold text-slate-900">{user.name}</h2>
              <p className="text-sm text-slate-500 mb-4">{user.role}</p>

              <div className="text-left bg-slate-50 p-4 rounded-xl border border-slate-100 space-y-3">
                <div>
                  <p className="text-xs font-medium text-slate-400">아이디 (로그인 ID)</p>
                  <p className="text-sm font-semibold text-slate-700">{user.username}</p>
                </div>
                {user.email && (
                  <div>
                    <p className="text-xs font-medium text-slate-400">이메일</p>
                    <p className="text-sm font-semibold text-slate-700">{user.email}</p>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* 비밀번호 변경 폼 영역 */}
          <div className="md:col-span-2">
            <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
              <div className="flex items-center gap-2 mb-6 border-b border-slate-100 pb-4">
                <Lock className="w-5 h-5 text-purple-600" />
                <h3 className="text-lg font-semibold text-slate-900">비밀번호 변경</h3>
              </div>

              <form onSubmit={handlePasswordChange} className="space-y-5">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">현재 비밀번호</label>
                  <input
                    type="password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    className="w-full px-4 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-purple-600 outline-none"
                    required
                  />
                </div>

                <div className="pt-2 border-t border-slate-100">
                  <label className="block text-sm font-medium text-slate-700 mb-1">새 비밀번호</label>
                  <input
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    className="w-full px-4 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-purple-600 outline-none"
                    required
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">새 비밀번호 확인</label>
                  <input
                    type="password"
                    value={confirmNewPassword}
                    onChange={(e) => setConfirmNewPassword(e.target.value)}
                    className="w-full px-4 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-purple-600 outline-none"
                    required
                  />
                </div>

                {message && (
                  <div className={`flex items-center gap-2 p-3 rounded-lg text-sm ${message.type === 'success' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : 'bg-red-50 text-red-700 border border-red-200'}`}>
                    <AlertCircle className="w-4 h-4" />
                    {message.text}
                  </div>
                )}

                <div className="pt-4 flex justify-end">
                  <button
                    type="submit"
                    disabled={isSubmitting || !currentPassword || !newPassword || !confirmNewPassword}
                    className="flex items-center gap-2 bg-purple-600 hover:bg-purple-700 text-white font-medium px-6 py-2.5 rounded-lg transition-colors disabled:opacity-50"
                  >
                    <Save className="w-4 h-4" />
                    {isSubmitting ? '저장 중...' : '비밀번호 저장'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}