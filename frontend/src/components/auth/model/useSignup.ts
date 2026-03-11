import { useState, useEffect, useMemo } from 'react';
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

const API_BASE_URL = 'http://localhost:8080/api/v1';

export function useSignup() {
  const router = useRouter();

  const [form, setForm] = useState({
    name: '',
    loginId: '',
    password: '',
    confirmPassword: '',
    email: '',
    phone: '', // 👈 전화번호 상태 유지
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

  // 💡 폼 유효성 검사 (인증 여부 조건은 임시 제외)
  const isValid = useMemo(() => {
    return !!(
      form.name.trim() &&
      form.loginId.trim() &&
      form.password.trim() &&
      form.confirmPassword.trim() &&
      form.email.trim() &&
      form.phone.trim() && // 전화번호 값 자체는 필수
      // isVerified && // 👈 인증 완료 필수 조건 임시 주석 처리
      rrnFirst6.length === 6 &&
      rrnBack7.length === 7 &&
      gender &&
      relationship &&
      (relationship !== 'OTHER' || customRelationship.trim()) &&
      selectedChild &&
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

  // 💡 인증코드 발송 로직 (추후 활성화를 위해 함수는 유지)
  const handleSendVerificationCode = async () => {
    if (!form.phone.trim()) {
      setVerificationMessage('연락처를 입력해주세요.');
      return;
    }

    setIsVerifying(true);
    setVerificationMessage('');

    try {
      const response = await fetch(`${API_BASE_URL}/auth/verification-codes`, {
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

  // 💡 인증코드 확인 로직 (추후 활성화를 위해 함수는 유지)
  const handleVerifyCode = async () => {
    if (!verificationCode.trim()) return;

    setIsVerifying(true);
    setVerificationMessage('');

    try {
      const response = await fetch(`${API_BASE_URL}/auth/verification-codes/verify`, {
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
      const response = await fetch(`${API_BASE_URL}/auth/common-codes?group=${encodeURIComponent(group)}`);
      if (!response.ok) throw new Error(`공통코드 조회 실패: ${group}`);
      return response.json();
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
      const response = await fetch(`${API_BASE_URL}/children?name=${encodeURIComponent(trimmed)}`);
      if (!response.ok) throw new Error('아이 조회에 실패했습니다.');
      const data = await response.json();
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
      const response = await fetch(`${API_BASE_URL}/auth/register`, {
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

      router.push('/auth/login');
    } catch (err) {
      setError(err instanceof Error ? err.message : '회원가입에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return {
    form, onChange, memberType, setMemberType,
    verificationCode, setVerificationCode, isCodeSent, isVerifying, isVerified, verificationMessage,
    handleSendVerificationCode, handleVerifyCode,
    childNameKeyword, setChildNameKeyword, selectedChild, isChildPopupOpen, setIsChildPopupOpen,
    childSearchKeyword, setChildSearchKeyword, childSearchResults, isChildSearching, childSearchError,
    searchChildren, openChildPopup, selectChild,
    rrnFirst6, setRrnFirst6, rrnBack7, onRrnBack7Change, gender, genderOptions,
    isPrimaryGuardian, setIsPrimaryGuardian, relationship, setRelationship, customRelationship, setCustomRelationship,
    filteredRelationshipOptions, agreeTerms, setAgreeTerms, error, isSubmitting, isValid, handleSubmit
  };
}