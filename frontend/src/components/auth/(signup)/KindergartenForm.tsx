import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

type CommonCodeItem = {
  code: string;
  codeName: string;
};

type KindergartenLookupItem = {
  kindergartenId: number;
  name: string;
  code: string | null;
  address: string | null;
  regionCode: string | null;
  businessRegistrationNo: string | null;
  contactName: string | null;
  contactPhone: string | null;
  contactEmail: string | null;
  status: string | null;
};

type KindergartenFormProps = {
  form: {
    name: string;
    loginId: string;
    email: string;
    phone: string;
    password: string;
    confirmPassword: string;
    staffNo: string;
    emergencyContactName: string;
    emergencyContactPhone: string;
    level: string;
  };
  onChange: (
    key:
      | 'name'
      | 'loginId'
      | 'password'
      | 'confirmPassword'
      | 'email'
      | 'phone'
      | 'staffNo'
      | 'emergencyContactName'
      | 'emergencyContactPhone'
      | 'level',
    value: string
  ) => void;
  kindergartenBizPart1: string;
  setKindergartenBizPart1: (value: string) => void;
  kindergartenBizPart2: string;
  setKindergartenBizPart2: (value: string) => void;
  kindergartenBizPart3: string;
  setKindergartenBizPart3: (value: string) => void;
  selectedKindergarten: KindergartenLookupItem | null;
  openKindergartenPopup: () => void;
  rrnFirst6: string;
  setRrnFirst6: (value: string) => void;
  rrnBack7: string;
  onRrnBack7Change: (value: string) => void;
  gender: string;
  genderOptions: CommonCodeItem[];
  teacherLevelOptions: CommonCodeItem[];
  fieldErrors: Partial<
    Record<
      | 'name'
      | 'loginId'
      | 'email'
      | 'phone'
      | 'password'
      | 'confirmPassword'
      | 'kindergarten'
      | 'staffNo'
      | 'rrn'
      | 'level'
      | 'emergencyContactName'
      | 'emergencyContactPhone',
      string
    >
  >;
  onAccountFieldBlur?: (field: 'loginId' | 'email' | 'phone', value: string) => void;
};

export function KindergartenForm({
  form,
  onChange,
  kindergartenBizPart1,
  setKindergartenBizPart1,
  kindergartenBizPart2,
  setKindergartenBizPart2,
  kindergartenBizPart3,
  setKindergartenBizPart3,
  selectedKindergarten,
  openKindergartenPopup,
  rrnFirst6,
  setRrnFirst6,
  rrnBack7,
  onRrnBack7Change,
  gender,
  genderOptions,
  teacherLevelOptions,
  fieldErrors,
  onAccountFieldBlur,
}: KindergartenFormProps) {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [showRrnBack7, setShowRrnBack7] = useState(false);

  const handleKindergartenEnter = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      openKindergartenPopup();
    }
  };

  return (
    <>
      <section className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
        <h2 className="text-sm font-semibold text-slate-700">유치원 관계자 정보</h2>
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
              onBlur={(e) => onAccountFieldBlur?.('loginId', e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              placeholder="kindergarten-staff-id"
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
              onBlur={(e) => onAccountFieldBlur?.('email', e.target.value)}
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
              onBlur={(e) => onAccountFieldBlur?.('phone', e.target.value)}
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

      <section>
        <h2 className="mb-3 text-sm font-semibold text-slate-700">유치원 찾기</h2>
        <div className="mb-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
          <p className="font-medium text-slate-800">유치원명 · 사업자번호 검색</p>
          <p className="mt-1 text-xs text-slate-600">
            사업자등록번호 10자리(XXX-XX-XXXXX)로 조회합니다. 목록에서 유치원명을 확인할 수 있습니다.
          </p>
        </div>
        <div className="flex flex-col gap-3 md:flex-row md:items-center">
          <div className="flex w-full flex-1 items-center gap-2">
            <input
              type="text"
              name="kindergartenBizPart1"
              value={kindergartenBizPart1}
              onChange={(e) => setKindergartenBizPart1(e.target.value.replace(/\D/g, '').slice(0, 3))}
              onKeyDown={handleKindergartenEnter}
              className="w-full min-w-0 rounded-lg border border-slate-300 bg-white px-3 py-2 text-center text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              placeholder="123"
              inputMode="numeric"
              maxLength={3}
              aria-label="사업자등록번호 앞 3자리"
            />
            <span className="shrink-0 text-slate-500">-</span>
            <input
              type="text"
              name="kindergartenBizPart2"
              value={kindergartenBizPart2}
              onChange={(e) => setKindergartenBizPart2(e.target.value.replace(/\D/g, '').slice(0, 2))}
              onKeyDown={handleKindergartenEnter}
              className="w-full min-w-0 rounded-lg border border-slate-300 bg-white px-3 py-2 text-center text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              placeholder="45"
              inputMode="numeric"
              maxLength={2}
              aria-label="사업자등록번호 중간 2자리"
            />
            <span className="shrink-0 text-slate-500">-</span>
            <input
              type="text"
              name="kindergartenBizPart3"
              value={kindergartenBizPart3}
              onChange={(e) => setKindergartenBizPart3(e.target.value.replace(/\D/g, '').slice(0, 5))}
              onKeyDown={handleKindergartenEnter}
              className="w-full min-w-0 rounded-lg border border-slate-300 bg-white px-3 py-2 text-center text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              placeholder="67890"
              inputMode="numeric"
              maxLength={5}
              aria-label="사업자등록번호 뒤 5자리"
            />
          </div>
          <button
            type="button"
            onClick={openKindergartenPopup}
            className="rounded-lg border border-emerald-500 px-4 py-2 text-emerald-700 hover:bg-emerald-50 md:min-w-[112px] whitespace-nowrap"
          >
            유치원 찾기
          </button>
        </div>
        <input type="hidden" name="kindergartenId" value={selectedKindergarten?.kindergartenId ?? ''} />
        {selectedKindergarten ? (
          <p className="mt-2 text-xs text-emerald-700">
            선택됨: {selectedKindergarten.name} (ID: {selectedKindergarten.kindergartenId}, 코드:{' '}
            {selectedKindergarten.code ?? '미입력'}, 사업자번호: {selectedKindergarten.businessRegistrationNo ?? '미입력'})
          </p>
        ) : (
          <p className="mt-2 text-xs text-slate-500">아직 선택된 유치원이 없습니다.</p>
        )}
        {fieldErrors.kindergarten && <p className="mt-1 text-xs text-red-500">{fieldErrors.kindergarten}</p>}
      </section>

      <section className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
        <h2 className="text-sm font-semibold text-slate-700">유치원 관계자 추가 정보</h2>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="mb-2 block text-sm font-medium text-slate-700">직원(사번)</label>
            <input
              type="text"
              name="staffNo"
              value={form.staffNo}
              onChange={(e) => onChange('staffNo', e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              placeholder="예: T-2026-001"
            />
            {fieldErrors.staffNo && <p className="mt-1 text-xs text-red-500">{fieldErrors.staffNo}</p>}
          </div>

          <div>
            <label className="mb-2 block text-sm font-medium text-slate-700">직급(level)</label>
            <select
              name="level"
              value={form.level}
              onChange={(e) => onChange('level', e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
            >
              <option value="">- 선택 -</option>
              {teacherLevelOptions.map((option) => (
                <option key={option.code} value={option.code}>
                  {option.codeName}
                </option>
              ))}
            </select>
            {fieldErrors.level && <p className="mt-1 text-xs text-red-500">{fieldErrors.level}</p>}
          </div>

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
              <div className="relative w-full">
                <input
                  type={showRrnBack7 ? 'text' : 'password'}
                  value={rrnBack7}
                  onChange={(e) => onRrnBack7Change(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 pr-11 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                  placeholder="1234567"
                  inputMode="numeric"
                  maxLength={7}
                />
                <button
                  type="button"
                  onClick={() => setShowRrnBack7((prev) => !prev)}
                  tabIndex={-1}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-700"
                  aria-label={showRrnBack7 ? '주민번호 뒷자리 숨기기' : '주민번호 뒷자리 보기'}
                >
                  {showRrnBack7 ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>
            {fieldErrors.rrn && <p className="mt-1 text-xs text-red-500">{fieldErrors.rrn}</p>}
          </div>

          <div>
            <p className="mb-2 text-sm font-medium text-slate-700">성별 (주민번호 기반 자동 선택)</p>
            <div className="flex gap-6 rounded-lg border border-slate-200 bg-white px-4 py-2">
              {genderOptions.map((option) => (
                <label key={option.code} className="flex items-center gap-2 text-sm text-slate-700">
                  <input type="radio" checked={gender === option.code} readOnly className="h-4 w-4" />
                  {option.codeName}
                </label>
              ))}
            </div>
          </div>

          <div>
            <label className="mb-2 block text-sm font-medium text-slate-700">비상 연락처 이름</label>
            <input
              type="text"
              name="emergencyContactName"
              value={form.emergencyContactName}
              onChange={(e) => onChange('emergencyContactName', e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              placeholder="예: 김보호"
            />
            {fieldErrors.emergencyContactName && <p className="mt-1 text-xs text-red-500">{fieldErrors.emergencyContactName}</p>}
          </div>

          <div>
            <label className="mb-2 block text-sm font-medium text-slate-700">비상 연락처 전화번호</label>
            <input
              type="tel"
              name="emergencyContactPhone"
              value={form.emergencyContactPhone}
              onChange={(e) => onChange('emergencyContactPhone', e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
              placeholder="010-0000-0000"
            />
            {fieldErrors.emergencyContactPhone && <p className="mt-1 text-xs text-red-500">{fieldErrors.emergencyContactPhone}</p>}
          </div>
        </div>
      </section>
    </>
  );
}
