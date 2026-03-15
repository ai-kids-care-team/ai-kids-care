import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

type PlatformItAdminFormProps = {
  form: {
    name: string;
    loginId: string;
    email: string;
    phone: string;
    password: string;
    confirmPassword: string;
  };
  onChange: (
    key: 'name' | 'loginId' | 'password' | 'confirmPassword' | 'email' | 'phone',
    value: string
  ) => void;
  fieldErrors: Partial<Record<'name' | 'loginId' | 'email' | 'phone' | 'password' | 'confirmPassword', string>>;
};

export function PlatformItAdminForm({ form, onChange, fieldErrors }: PlatformItAdminFormProps) {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  return (
    <section className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
      <h2 className="text-sm font-semibold text-slate-700">플랫폼 관리자 정보</h2>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div>
          <label className="mb-2 block text-sm font-medium text-slate-700">이름</label>
          <input
            type="text"
            name="name"
            value={form.name}
            onChange={(e) => onChange('name', e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
            placeholder="홍길동"
            required
          />
          {fieldErrors.name && <p className="mt-1 text-xs text-red-500">{fieldErrors.name}</p>}
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-slate-700">로그인 ID</label>
          <input
            type="text"
            name="loginId"
            value={form.loginId}
            onChange={(e) => onChange('loginId', e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
            placeholder="platform-admin-id"
            required
          />
          {fieldErrors.loginId && <p className="mt-1 text-xs text-red-500">{fieldErrors.loginId}</p>}
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-slate-700">이메일</label>
          <input
            type="email"
            name="email"
            value={form.email}
            onChange={(e) => onChange('email', e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
            placeholder="email@example.com"
            required
          />
          {fieldErrors.email && <p className="mt-1 text-xs text-red-500">{fieldErrors.email}</p>}
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-slate-700">전화번호</label>
          <input
            type="tel"
            name="phone"
            value={form.phone}
            onChange={(e) => onChange('phone', e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
            placeholder="010-0000-0000"
            required
          />
          {fieldErrors.phone && <p className="mt-1 text-xs text-red-500">{fieldErrors.phone}</p>}
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-slate-700">비밀번호</label>
          <div className="relative">
            <input
              type={showPassword ? 'text' : 'password'}
              name="password"
              value={form.password}
              onChange={(e) => onChange('password', e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 pr-11 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              placeholder="••••••••"
              required
            />
            <button
              type="button"
              onClick={() => setShowPassword((prev) => !prev)}
              tabIndex={-1}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-700"
              aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
            >
              {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
            </button>
          </div>
          {fieldErrors.password && <p className="mt-1 text-xs text-red-500">{fieldErrors.password}</p>}
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-slate-700">비밀번호 확인</label>
          <div className="relative">
            <input
              type={showConfirmPassword ? 'text' : 'password'}
              name="confirmPassword"
              value={form.confirmPassword}
              onChange={(e) => onChange('confirmPassword', e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 pr-11 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              placeholder="••••••••"
              required
            />
            <button
              type="button"
              onClick={() => setShowConfirmPassword((prev) => !prev)}
              tabIndex={-1}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-700"
              aria-label={showConfirmPassword ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'}
            >
              {showConfirmPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
            </button>
          </div>
          {fieldErrors.confirmPassword && <p className="mt-1 text-xs text-red-500">{fieldErrors.confirmPassword}</p>}
        </div>
      </div>
    </section>
  );
}
