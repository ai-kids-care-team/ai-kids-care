import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

type ChildLookupItem = {
  childId: number;
  className: string | null;
  name: string;
};

type CommonCodeItem = {
  code: string;
  codeName: string;
};

type GuardianFormProps = {
  form: {
    name: string;
    loginId: string;
    email: string;
    phone: string;
    password: string;
    confirmPassword: string;
  };
  onChange: (key: 'name' | 'loginId' | 'password' | 'confirmPassword' | 'email' | 'phone', value: string) => void;
  childNameKeyword: string;
  setChildNameKeyword: (value: string) => void;
  selectedChild: ChildLookupItem | null;
  openChildPopup: () => void;
  rrnFirst6: string;
  setRrnFirst6: (value: string) => void;
  rrnBack7: string;
  onRrnBack7Change: (value: string) => void;
  gender: string;
  genderOptions: CommonCodeItem[];
  relationship: string;
  setRelationship: (value: string) => void;
  filteredRelationshipOptions: CommonCodeItem[];
  customRelationship: string;
  setCustomRelationship: (value: string) => void;
  isPrimaryGuardian: boolean;
  setIsPrimaryGuardian: (value: boolean) => void;
  fieldErrors: Partial<
    Record<
      'name' | 'loginId' | 'email' | 'phone' | 'password' | 'confirmPassword' | 'child' | 'rrn' | 'relationship',
      string
    >
  >;
};

export function GuardianForm({
  form,
  onChange,
  childNameKeyword,
  setChildNameKeyword,
  selectedChild,
  openChildPopup,
  rrnFirst6,
  setRrnFirst6,
  rrnBack7,
  onRrnBack7Change,
  gender,
  genderOptions,
  relationship,
  setRelationship,
  filteredRelationshipOptions,
  customRelationship,
  setCustomRelationship,
  isPrimaryGuardian,
  setIsPrimaryGuardian,
  fieldErrors,
}: GuardianFormProps) {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const handleChildNameKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      openChildPopup();
    }
  };

  return (
    <>
      <section className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
        <h2 className="text-sm font-semibold text-slate-700">양육자 정보</h2>
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
              placeholder="your-id"
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
          <div className="flex flex-col gap-2">
            <label className="block text-sm font-medium text-slate-700">연락처</label>
            <div className="flex gap-2">
              <input
                type="tel"
                name="phone"
                value={form.phone}
                onChange={(e) => onChange('phone', e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                placeholder="010-0000-0000"
                required
              />
            </div>
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

      <section>
        <h2 className="mb-3 text-sm font-semibold text-slate-700">아이 찾기</h2>
        <div className="flex flex-col gap-3 md:flex-row md:items-stretch">
          <input
            type="text"
            name="childNameKeyword"
            value={childNameKeyword}
            onChange={(e) => setChildNameKeyword(e.target.value)}
            onKeyDown={handleChildNameKeyDown}
            className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500 md:w-[85%]"
            placeholder="아이 이름 입력 후 Enter"
          />
          <button
            type="button"
            onClick={openChildPopup}
            className="rounded-lg border border-emerald-500 px-4 py-2 text-emerald-700 hover:bg-emerald-50 md:w-[15%] md:min-w-[96px] whitespace-nowrap"
          >
            아이 찾기
          </button>
        </div>
        <input type="hidden" name="childId" value={selectedChild?.childId ?? ''} />
        {selectedChild ? (
          <p className="mt-2 text-xs text-emerald-700">
            선택됨: {selectedChild.name} (ID: {selectedChild.childId}, 반: {selectedChild.className ?? '미지정'})
          </p>
        ) : (
          <p className="mt-2 text-xs text-slate-500">아직 선택된 아이가 없습니다.</p>
        )}
        {fieldErrors.child && <p className="mt-1 text-xs text-red-500">{fieldErrors.child}</p>}
      </section>

      <section className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
        <h2 className="text-sm font-semibold text-slate-700">양육자 추가 정보</h2>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="mb-2 block text-sm font-medium text-slate-700">주민등록번호 앞자리</label>
            <div className="flex items-center gap-2">
              <input
                type="text"
                name="rrnFirst6"
                value={rrnFirst6}
                onChange={(e) => setRrnFirst6(e.target.value.replace(/\D/g, '').slice(0, 6))}
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                placeholder="900101"
                inputMode="numeric"
                maxLength={6}
              />
              <span className="text-slate-500">-</span>
              <input
                type="password"
                value={rrnBack7}
                onChange={(e) => onRrnBack7Change(e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                placeholder="1234567"
                inputMode="numeric"
                maxLength={7}
              />
            </div>
            <p className="mt-2 text-xs text-slate-500">숫자만 입력하세요. 뒷자리 첫 숫자로 성별이 자동 선택됩니다.</p>
            {fieldErrors.rrn && <p className="mt-1 text-xs text-red-500">{fieldErrors.rrn}</p>}
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium text-slate-700">주민등록번호 뒷자리</label>
            <p className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm text-slate-600">
              보안상 화면에는 마스킹되어 표시됩니다.
            </p>
          </div>
        </div>

        <div>
          <p className="mb-2 text-sm font-medium text-slate-700">성별 (주민번호 기반 자동 선택)</p>
          <div className="flex gap-6">
            {genderOptions.map((option) => (
              <label key={option.code} className="flex items-center gap-2 text-sm text-slate-700">
                <input type="radio" checked={gender === option.code} readOnly className="h-4 w-4" />
                {option.codeName}
              </label>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="mb-2 block text-sm font-medium text-slate-700">관계</label>
            <select
              name="relationship"
              value={relationship}
              onChange={(e) => setRelationship(e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
            >
              <option value="">- 선택 -</option>
              {filteredRelationshipOptions.map((option) => (
                <option key={option.code} value={option.code}>
                  {option.codeName}
                </option>
              ))}
            </select>
          </div>
          {relationship === 'OTHER' && (
            <div>
              <label className="mb-2 block text-sm font-medium text-slate-700">기타 관계 입력</label>
              <input
                type="text"
                value={customRelationship}
                onChange={(e) => setCustomRelationship(e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                placeholder="예: 고모"
              />
            </div>
          )}
        </div>
        {fieldErrors.relationship && <p className="mt-1 text-xs text-red-500">{fieldErrors.relationship}</p>}

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={isPrimaryGuardian}
            onChange={(e) => setIsPrimaryGuardian(e.target.checked)}
            className="h-4 w-4 rounded border-slate-300 bg-white"
          />
          주된 보호자
        </label>
      </section>
    </>
  );
}
