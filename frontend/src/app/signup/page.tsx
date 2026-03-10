'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

type MemberType = 'GUARDIAN' | 'TEACHER' | 'KINDERGARTEN_ADMIN' | 'PLATFORM_IT_ADMIN';
type ChildLookupItem = {
  childId: number;
  kindergartenId: number;
  classId: number | null;
  className: string | null;
  name: string;
  childNo: string | null;
  birthDate: string | null;
  gender: string | null;
};
type GenderChoice = 'MALE' | 'FEMALE' | '';
type CommonCodeItem = {
  codeGroup: string;
  parentCode?: string | null;
  code: string;
  codeName: string;
  sortOrder: number;
};

const FALLBACK_GENDER_OPTIONS: CommonCodeItem[] = [
  { codeGroup: 'GENDER', parentCode: null, code: 'MALE', codeName: '남자', sortOrder: 1 },
  { codeGroup: 'GENDER', parentCode: null, code: 'FEMALE', codeName: '여자', sortOrder: 2 },
];

const FALLBACK_RELATIONSHIP_OPTIONS: CommonCodeItem[] = [
  { codeGroup: 'GUARDIAN_RELATIONSHIP', parentCode: 'FEMALE', code: 'MOTHER', codeName: '엄마', sortOrder: 1 },
  { codeGroup: 'GUARDIAN_RELATIONSHIP', parentCode: 'MALE', code: 'FATHER', codeName: '아빠', sortOrder: 2 },
  { codeGroup: 'GUARDIAN_RELATIONSHIP', parentCode: 'FEMALE', code: 'MATERNAL_GRANDMOTHER', codeName: '외할머니', sortOrder: 3 },
  { codeGroup: 'GUARDIAN_RELATIONSHIP', parentCode: 'MALE', code: 'MATERNAL_GRANDFATHER', codeName: '외할아버지', sortOrder: 4 },
  { codeGroup: 'GUARDIAN_RELATIONSHIP', parentCode: 'FEMALE', code: 'PATERNAL_GRANDMOTHER', codeName: '친할머니', sortOrder: 5 },
  { codeGroup: 'GUARDIAN_RELATIONSHIP', parentCode: 'MALE', code: 'PATERNAL_GRANDFATHER', codeName: '친할아버지', sortOrder: 6 },
  { codeGroup: 'GUARDIAN_RELATIONSHIP', parentCode: null, code: 'OTHER', codeName: '기타', sortOrder: 99 },
];

const MEMBER_TYPES: Array<{ value: MemberType; label: string; description: string }> = [
  { value: 'GUARDIAN', label: '양육자', description: '자녀 정보 조회 및 알림 확인' },
  { value: 'TEACHER', label: '유치원 교사', description: '반/원아 상태 관리 및 기록' },
  { value: 'KINDERGARTEN_ADMIN', label: '유치원 관리자', description: '교사/반 운영 및 권한 관리' },
  { value: 'PLATFORM_IT_ADMIN', label: '플랫폼 관리자', description: '시스템 운영 및 모니터링' },
];

// const API_URL = process.env.NEXT_PUBLIC_API_URL !== undefined
//   ? process.env.NEXT_PUBLIC_API_URL
//   : 'http://localhost:8081';

const API_URL = 'http://localhost:8081';

export default function SignupPage() {
  const router = useRouter();
  const [form, setForm] = useState({
    name: '',
    loginId: '',
    password: '',
    confirmPassword: '',
    email: '',
    phone: '',
  });

  // 인증 관련 추가 상태 (현재는 UI에서 숨겨둠)
  const [verificationCode, setVerificationCode] = useState('');
  const [isCodeSent, setIsCodeSent] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);
  const [isVerified, setIsVerified] = useState(false);
  const [verificationMessage, setVerificationMessage] = useState('');

  const [memberType, setMemberType] = useState<MemberType>('GUARDIAN');
  const [childNameKeyword, setChildNameKeyword] = useState('');
  const [selectedChild, setSelectedChild] = useState<ChildLookupItem | null>(null);
  const [isChildPopupOpen, setIsChildPopupOpen] = useState(false);
  const [childSearchKeyword, setChildSearchKeyword] = useState('');
  const [childSearchResults, setChildSearchResults] = useState<ChildLookupItem[]>([]);
  const [isChildSearching, setIsChildSearching] = useState(false);
  const [childSearchError, setChildSearchError] = useState('');
  const [rrnFirst6, setRrnFirst6] = useState('');
  const [rrnBack7, setRrnBack7] = useState('');
  const [gender, setGender] = useState<GenderChoice>('');
  const [isPrimaryGuardian, setIsPrimaryGuardian] = useState(false);
  const [relationship, setRelationship] = useState('');
  const [customRelationship, setCustomRelationship] = useState('');
  const [genderOptions, setGenderOptions] = useState<CommonCodeItem[]>(FALLBACK_GENDER_OPTIONS);
  const [relationshipOptions, setRelationshipOptions] = useState<CommonCodeItem[]>(FALLBACK_RELATIONSHIP_OPTIONS);
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const filteredRelationshipOptions = useMemo(() => {
    if (!gender) return relationshipOptions;
    return relationshipOptions.filter((option) => {
      if (option.code === 'OTHER') return true;
      if (!option.parentCode) return true;
      return option.parentCode.toUpperCase() === gender;
    });
  }, [relationshipOptions, gender]);

  // 💡 폼 유효성 검사 (인증 여부 조건 제외)
  const isValid = useMemo(() => {
    return (
      form.name.trim() &&
      form.loginId.trim() &&
      form.password.trim() &&
      form.confirmPassword.trim() &&
      form.email.trim() &&
      form.phone.trim() &&
      // isVerified && // 👈 인증 완료 필수 조건 임시 주석 처리
      rrnFirst6.length === 6 &&
      rrnBack7.length === 7 &&
      !!gender &&
      !!relationship &&
      (relationship !== 'OTHER' || !!customRelationship.trim()) &&
      !!selectedChild &&
      agreeTerms
    );
  }, [form, rrnFirst6, rrnBack7, gender, relationship, customRelationship, selectedChild, agreeTerms]);

  const onChange = (key: keyof typeof form, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    if (key === 'phone') {
      setIsCodeSent(false);
      setIsVerified(false);
      setVerificationCode('');
      setVerificationMessage('');
    }
  };

  const handleSendVerificationCode = async () => {
    if (!form.phone.trim()) {
      setVerificationMessage('연락처를 입력해주세요.');
      return;
    }

    setIsVerifying(true);
    setVerificationMessage('');

    try {
      const response = await fetch(`${API_URL}/v1/auth/verification-codes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: 'PHONE', target: form.phone }),
      });

      if (!response.ok) throw new Error('인증코드 발송에 실패했습니다.');

      setIsCodeSent(true);
      setVerificationMessage('인증코드가 발송되었습니다. (3분 이내 입력)');
    } catch (err) {
      setVerificationMessage(err instanceof Error ? err.message : '오류가 발생했습니다.');
    } finally {
      setIsVerifying(false);
    }
  };

  const handleVerifyCode = async () => {
    if (!verificationCode.trim()) return;

    setIsVerifying(true);
    setVerificationMessage('');

    try {
      const response = await fetch(`${API_URL}/v1/auth/verification-codes/verify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ target: form.phone, code: verificationCode }),
      });

      if (!response.ok) throw new Error('잘못된 인증코드이거나 만료되었습니다.');

      setIsVerified(true);
      setVerificationMessage('연락처 인증이 완료되었습니다.');
    } catch (err) {
      setVerificationMessage(err instanceof Error ? err.message : '오류가 발생했습니다.');
    } finally {
      setIsVerifying(false);
    }
  };

  const onRrnBack7Change = (value: string) => {
    const onlyDigits = value.replace(/\D/g, '').slice(0, 7);
    setRrnBack7(onlyDigits);

    const first = onlyDigits.charAt(0);
    if (['1', '3', '5', '7'].includes(first)) {
      setGender('MALE');
    } else if (['2', '4', '6', '8'].includes(first)) {
      setGender('FEMALE');
    } else {
      setGender('');
    }
  };

  useEffect(() => {
    const fetchCommonCodes = async (group: string): Promise<CommonCodeItem[]> => {
      const response = await fetch(`${API_URL}/api/auth/common-codes?group=${encodeURIComponent(group)}`);
      if (!response.ok) throw new Error(`공통코드 조회 실패: ${group}`);
      return response.json() as Promise<CommonCodeItem[]>;
    };

    const loadCommonCodes = async () => {
      try {
        const [genderCodes, relationshipCodes] = await Promise.all([
          fetchCommonCodes('GENDER'),
          fetchCommonCodes('GUARDIAN_RELATIONSHIP'),
        ]);

        if (genderCodes.length > 0) setGenderOptions(genderCodes);
        if (relationshipCodes.length > 0) setRelationshipOptions(relationshipCodes);
      } catch {
        // fallback
      }
    };

    void loadCommonCodes();
  }, []);

  useEffect(() => {
    const exists = filteredRelationshipOptions.some((option) => option.code === relationship);
    if (relationship && !exists) {
      setRelationship('');
      setCustomRelationship('');
    }
  }, [filteredRelationshipOptions, relationship]);

  const searchChildren = async (keyword: string) => {
    const trimmed = keyword.trim();
    if (!trimmed) {
      setChildSearchError('아이 이름을 입력해주세요.');
      setChildSearchResults([]);
      return;
    }

    setChildSearchError('');
    setIsChildSearching(true);
    try {
      const response = await fetch(`${API_URL}/api/auth/children?name=${encodeURIComponent(trimmed)}`);
      if (!response.ok) throw new Error('아이 조회에 실패했습니다.');
      const data = (await response.json()) as ChildLookupItem[];
      setChildSearchResults(data);
      if (data.length === 0) setChildSearchError('검색 결과가 없습니다.');
    } catch (err) {
      setChildSearchResults([]);
      setChildSearchError(err instanceof Error ? err.message : '아이 조회에 실패했습니다.');
    } finally {
      setIsChildSearching(false);
    }
  };

  const openChildPopup = () => {
    const keyword = childNameKeyword.trim();
    setChildSearchKeyword(keyword);
    setChildSearchResults([]);
    setChildSearchError('');
    setIsChildPopupOpen(true);
    if (keyword) void searchChildren(keyword);
  };

  const handleChildNameKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      openChildPopup();
    }
  };

  const selectChild = (child: ChildLookupItem) => {
    setSelectedChild(child);
    setChildNameKeyword(child.name);
    setIsChildPopupOpen(false);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (memberType !== 'GUARDIAN') {
      setError('현재는 양육자 회원가입만 지원합니다.');
      return;
    }

    // 💡 연락처 인증 확인 로직 임시 주석 처리
    /*
    if (!isVerified) {
      setError('연락처 인증을 완료해주세요.');
      return;
    }
    */

    if (form.password !== form.confirmPassword) {
      setError('비밀번호와 비밀번호 확인이 일치하지 않습니다.');
      return;
    }
    if (!selectedChild) {
      setError('아이 찾기에서 아이를 선택해주세요.');
      return;
    }
    if (rrnFirst6.length !== 6 || rrnBack7.length !== 7) {
      setError('주민등록번호를 정확히 입력해주세요.');
      return;
    }
    if (!relationship) {
      setError('관계를 선택해주세요.');
      return;
    }
    if (relationship === 'OTHER' && !customRelationship.trim()) {
      setError('기타 관계를 입력해주세요.');
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await fetch(`${API_URL}/v1/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: form.name,
          loginId: form.loginId,
          password: form.password,
          email: form.email,
          phone: form.phone,
          memberType,
          childId: selectedChild.childId,
          rrnFirst6,
          rrnBack7,
          relationship,
          customRelationship: relationship === 'OTHER' ? customRelationship.trim() : '',
          primaryGuardian: isPrimaryGuardian,
        }),
      });

      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.error || '회원가입에 실패했습니다.');
      }

      router.push('/login');
    } catch (err) {
      setError(err instanceof Error ? err.message : '회원가입에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 py-10 px-4">
      <div className="mx-auto w-full max-w-3xl rounded-2xl border border-slate-700 bg-slate-800 p-8 shadow-xl">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-white">회원가입</h1>
          <p className="mt-2 text-slate-400 text-sm">
            회원 정보를 입력하고 회원유형을 선택해 계정을 생성하세요.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <section>
            <h2 className="mb-3 text-sm font-semibold text-slate-300">회원유형</h2>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {MEMBER_TYPES.map((type) => {
                const selected = memberType === type.value;
                return (
                  <button
                    key={type.value}
                    type="button"
                    onClick={() => setMemberType(type.value)}
                    className={`rounded-xl border p-4 text-left transition-colors ${
                      selected
                        ? 'border-cyan-500 bg-cyan-500/10'
                        : 'border-slate-600 bg-slate-700/30 hover:border-slate-500'
                    }`}
                  >
                    <p className="font-semibold text-white">{type.label}</p>
                    <p className="mt-1 text-xs text-slate-400">{type.description}</p>
                  </button>
                );
              })}
            </div>
          </section>

          <section className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <label className="mb-2 block text-sm font-medium text-slate-300">이름</label>
              <input
                type="text"
                value={form.name}
                onChange={(e) => onChange('name', e.target.value)}
                className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
                placeholder="홍길동"
                required
              />
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-slate-300">로그인 ID</label>
              <input
                type="text"
                value={form.loginId}
                onChange={(e) => onChange('loginId', e.target.value)}
                className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
                placeholder="your-id"
                required
              />
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-slate-300">이메일</label>
              <input
                type="email"
                value={form.email}
                onChange={(e) => onChange('email', e.target.value)}
                className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
                placeholder="email@example.com"
                required
              />
            </div>

            {/* 💡 연락처 UI 수정 영역 (인증 버튼 및 입력칸 숨김) */}
            <div className="flex flex-col gap-2">
              <label className="block text-sm font-medium text-slate-300">연락처</label>
              <div className="flex gap-2">
                <input
                  type="tel"
                  value={form.phone}
                  onChange={(e) => onChange('phone', e.target.value)}
                  // disabled={isVerified} 임시 해제
                  className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
                  placeholder="010-0000-0000"
                  required
                />
                {/* <button
                  type="button"
                  onClick={handleSendVerificationCode}
                  disabled={isVerifying || isVerified || !form.phone}
                  className="whitespace-nowrap rounded-lg border border-cyan-500 px-4 py-2 text-sm text-cyan-300 hover:bg-cyan-500/10 disabled:opacity-50"
                >
                  {isVerified ? '인증완료' : isCodeSent ? '재발송' : '인증번호 발송'}
                </button>
                */}
              </div>

              {/* {isCodeSent && !isVerified && (
                <div className="flex gap-2 mt-1">
                  <input
                    type="text"
                    value={verificationCode}
                    onChange={(e) => setVerificationCode(e.target.value)}
                    className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
                    placeholder="인증번호 6자리"
                    maxLength={6}
                  />
                  <button
                    type="button"
                    onClick={handleVerifyCode}
                    disabled={isVerifying || !verificationCode}
                    className="whitespace-nowrap rounded-lg bg-cyan-600 px-4 py-2 text-sm font-medium text-white hover:bg-cyan-500 disabled:opacity-50"
                  >
                    {isVerifying ? '확인 중...' : '확인'}
                  </button>
                </div>
              )}
              {verificationMessage && (
                <p className={`text-xs ${isVerified ? 'text-emerald-400' : 'text-amber-400'}`}>
                  {verificationMessage}
                </p>
              )}
              */}
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-slate-300">비밀번호</label>
              <input
                type="password"
                value={form.password}
                onChange={(e) => onChange('password', e.target.value)}
                className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
                placeholder="••••••••"
                required
              />
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-slate-300">비밀번호 확인</label>
              <input
                type="password"
                value={form.confirmPassword}
                onChange={(e) => onChange('confirmPassword', e.target.value)}
                className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
                placeholder="••••••••"
                required
              />
            </div>
          </section>

          <section>
            <h2 className="mb-3 text-sm font-semibold text-slate-300">아이 찾기</h2>
            <div className="flex flex-col gap-3 md:flex-row">
              <input
                type="text"
                value={childNameKeyword}
                onChange={(e) => setChildNameKeyword(e.target.value)}
                onKeyDown={handleChildNameKeyDown}
                className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
                placeholder="아이 이름 입력 후 Enter"
              />
              <button
                type="button"
                onClick={openChildPopup}
                className="rounded-lg border border-cyan-500 px-4 py-2 text-cyan-300 hover:bg-cyan-500/10"
              >
                아이 찾기
              </button>
            </div>
            <input type="hidden" name="childId" value={selectedChild?.childId ?? ''} />
            {selectedChild ? (
              <p className="mt-2 text-xs text-emerald-300">
                선택됨: {selectedChild.name} (ID: {selectedChild.childId}, 반: {selectedChild.className ?? '미지정'})
              </p>
            ) : (
              <p className="mt-2 text-xs text-slate-400">아직 선택된 아이가 없습니다.</p>
            )}
          </section>

          <section className="space-y-4 rounded-xl border border-slate-700 bg-slate-700/20 p-4">
            <h2 className="text-sm font-semibold text-slate-200">양육자 추가 정보</h2>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-300">주민등록번호 앞자리</label>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    value={rrnFirst6}
                    onChange={(e) => setRrnFirst6(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
                    placeholder="900101"
                    inputMode="numeric"
                    maxLength={6}
                  />
                  <span className="text-slate-400">-</span>
                  <input
                    type="password"
                    value={rrnBack7}
                    onChange={(e) => onRrnBack7Change(e.target.value)}
                    className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
                    placeholder="1234567"
                    inputMode="numeric"
                    maxLength={7}
                  />
                </div>
                <p className="mt-2 text-xs text-slate-400">숫자만 입력하세요. 뒷자리 첫 숫자로 성별이 자동 선택됩니다.</p>
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-300">주민등록번호 뒷자리</label>
                <p className="rounded-lg border border-slate-700 bg-slate-700/30 px-4 py-2 text-sm text-slate-300">
                  보안상 화면에는 마스킹되어 표시됩니다.
                </p>
              </div>
            </div>

            <div>
              <p className="mb-2 text-sm font-medium text-slate-300">성별 (주민번호 기반 자동 선택)</p>
              <div className="flex gap-6">
                {genderOptions.map((option) => (
                  <label key={option.code} className="flex items-center gap-2 text-sm text-slate-200">
                    <input type="radio" checked={gender === option.code} readOnly className="h-4 w-4" />
                    {option.codeName}
                  </label>
                ))}
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-300">관계</label>
                <select
                  value={relationship}
                  onChange={(e) => setRelationship(e.target.value)}
                  className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
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
                  <label className="mb-2 block text-sm font-medium text-slate-300">기타 관계 입력</label>
                  <input
                    type="text"
                    value={customRelationship}
                    onChange={(e) => setCustomRelationship(e.target.value)}
                    className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
                    placeholder="예: 고모"
                  />
                </div>
              )}
            </div>

            <label className="flex items-center gap-2 text-sm text-slate-300">
              <input
                type="checkbox"
                checked={isPrimaryGuardian}
                onChange={(e) => setIsPrimaryGuardian(e.target.checked)}
                className="h-4 w-4 rounded border-slate-500 bg-slate-700"
              />
              주된 보호자
            </label>
          </section>

          <label className="flex items-center gap-2 text-sm text-slate-300">
            <input
              type="checkbox"
              checked={agreeTerms}
              onChange={(e) => setAgreeTerms(e.target.checked)}
              className="h-4 w-4 rounded border-slate-500 bg-slate-700"
            />
            서비스 이용약관 및 개인정보 처리방침에 동의합니다.
          </label>

          {error && <p className="text-sm text-red-400">{error}</p>}

          <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-end">
            <Link
              href="/login"
              className="rounded-lg border border-slate-600 px-4 py-2 text-center text-slate-300 hover:bg-slate-700"
            >
              로그인으로 돌아가기
            </Link>
            <button
              type="submit"
              disabled={!isValid || isSubmitting}
              className="rounded-lg bg-cyan-600 px-4 py-2 font-medium text-white hover:bg-cyan-500 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isSubmitting ? '가입 처리 중...' : '회원가입'}
            </button>
          </div>
        </form>
      </div>

      {isChildPopupOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
          <div className="w-full max-w-4xl rounded-2xl border border-slate-700 bg-slate-800 p-6 shadow-2xl">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-xl font-semibold text-white">아이 찾기</h2>
              <button
                type="button"
                onClick={() => setIsChildPopupOpen(false)}
                className="rounded-md border border-slate-600 px-3 py-1 text-sm text-slate-300 hover:bg-slate-700"
              >
                닫기
              </button>
            </div>

            <div className="mb-4 flex flex-col gap-3 md:flex-row">
              <input
                type="text"
                value={childSearchKeyword}
                onChange={(e) => setChildSearchKeyword(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    void searchChildren(childSearchKeyword);
                  }
                }}
                className="w-full rounded-lg border border-slate-600 bg-slate-700 px-4 py-2 text-white focus:border-transparent focus:ring-2 focus:ring-cyan-500"
                placeholder="아이 이름 검색 (예: 김하린 or 이준호 or 박서윤 or 최민우 or 정지안 or 박서윤)"
              />
              <button
                type="button"
                onClick={() => void searchChildren(childSearchKeyword)}
                className="rounded-lg bg-cyan-600 px-4 py-2 font-medium text-white hover:bg-cyan-500"
              >
                검색
              </button>
            </div>

            {childSearchError && <p className="mb-3 text-sm text-amber-300">{childSearchError}</p>}
            {isChildSearching && <p className="mb-3 text-sm text-slate-300">검색 중...</p>}

            <div className="max-h-72 overflow-auto rounded-lg border border-slate-700">
              <table className="min-w-full text-sm text-slate-200">
                <thead className="bg-slate-700/50 text-xs uppercase text-slate-300">
                  <tr>
                    <th className="px-3 py-2 text-left">ID</th>
                    <th className="px-3 py-2 text-left">이름</th>
                    <th className="px-3 py-2 text-left">원아번호</th>
                    <th className="px-3 py-2 text-left">생년월일</th>
                    <th className="px-3 py-2 text-left">성별</th>
                    <th className="px-3 py-2 text-left">반</th>
                    <th className="px-3 py-2 text-left">선택</th>
                  </tr>
                </thead>
                <tbody>
                  {childSearchResults.map((child) => (
                    <tr key={child.childId} className="border-t border-slate-700">
                      <td className="px-3 py-2">{child.childId}</td>
                      <td className="px-3 py-2">{child.name}</td>
                      <td className="px-3 py-2">{child.childNo ?? '-'}</td>
                      <td className="px-3 py-2">{child.birthDate ?? '-'}</td>
                      <td className="px-3 py-2">{child.gender ?? '-'}</td>
                      <td className="px-3 py-2">{child.className ?? '-'}</td>
                      <td className="px-3 py-2">
                        <button
                          type="button"
                          onClick={() => selectChild(child)}
                          className="rounded-md bg-emerald-600 px-3 py-1 text-xs font-medium text-white hover:bg-emerald-500"
                        >
                          선택
                        </button>
                      </td>
                    </tr>
                  ))}
                  {childSearchResults.length === 0 && !isChildSearching && (
                    <tr>
                      <td className="px-3 py-6 text-center text-slate-400" colSpan={7}>
                        검색 결과가 없습니다.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}