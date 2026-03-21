import { useState, useEffect, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { useLoginMutation } from '@/services/apis/auth.api';
import { useAppDispatch } from '@/store/hook';
import { setCredentials } from '@/store/slices/userSlice';
import type { UserRole } from '@/types/anomaly';
import { API_BASE_URL, LEGACY_API_BASE_URL } from '@/config/api';

type MemberType = 'GUARDIAN' | 'KINDERGARTEN' | 'SUPERADMIN' | 'PLATFORM_IT_ADMIN';
type FieldErrorKey =
  | 'name'
  | 'loginId'
  | 'email'
  | 'phone'
  | 'password'
  | 'confirmPassword'
  | 'department'
  | 'kindergarten'
  | 'staffNo'
  | 'level'
  | 'startDate'
  | 'endDate'
  | 'emergencyContactName'
  | 'emergencyContactPhone'
  | 'child'
  | 'rrn'
  | 'relationship'
  | 'agreeTerms';
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

type ChildApiItem = {
  childId?: number;
  id?: number;
  kindergartenId?: number;
  classId?: number | null;
  className?: string | null;
  name?: string;
  childNo?: string | null;
  birthDate?: string | null;
  gender?: string | null;
  kindergarten?: { id?: number };
  class?: { id?: number; name?: string | null };
};
type GenderChoice = 'MALE' | 'FEMALE' | '';
type CommonCodeItem = {
  codeGroup: string;
  parentCode?: string | null;
  code: string;
  codeName: string;
  sortOrder: number;
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

const FALLBACK_TEACHER_LEVEL_OPTIONS: CommonCodeItem[] = [
  { codeGroup: 'teachers', parentCode: 'level', code: 'PRINCIPAL', codeName: '원장', sortOrder: 1 },
  { codeGroup: 'teachers', parentCode: 'level', code: 'VICE_PRINCIPAL', codeName: '부원장', sortOrder: 2 },
  { codeGroup: 'teachers', parentCode: 'level', code: 'TEACHER', codeName: '일반교사', sortOrder: 3 },
];

const normalizeLoginId = (value: string) => value.replace(/[^A-Za-z0-9]/g, '');
const DATE_RANGE_ERROR_MESSAGE = '근무종료일은 근무시작일보다 빠를 수 없습니다.';
const DATE_RANGE_GUIDE_MESSAGE = '날짜 범위가 올바르지 않습니다. 근무시작일/근무종료일을 확인해주세요.';
const INITIAL_FORM_STATE = {
  name: '',
  loginId: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: '',
  department: '',
  staffNo: '',
  emergencyContactName: '',
  emergencyContactPhone: '',
  level: '',
  startDate: '',
  endDate: '',
};

const mapBackendRoleToFrontendRole = (role: string): UserRole => {
  const normalized = String(role ?? '').trim().toUpperCase();

  if (normalized === 'SUPERADMIN' || normalized === 'SUPER_ADMIN') return 'super_admin';
  if (normalized === 'PLATFORM_IT_ADMIN' || normalized === 'SYSTEM_ADMIN') return 'system_admin';
  if (normalized === 'KINDERGARTEN_ADMIN' || normalized === 'ADMIN') return 'admin';
  if (normalized === 'TEACHER') return 'teacher';
  return 'guardian';
};

export function useSignup() {
  const router = useRouter();
  const dispatch = useAppDispatch();
  const [loginApi] = useLoginMutation();

  const [form, setForm] = useState(INITIAL_FORM_STATE);

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
  const [childSearchFirst6, setChildSearchFirst6] = useState('');
  const [childSearchBack7, setChildSearchBack7] = useState('');
  const [childSearchResults, setChildSearchResults] = useState<ChildLookupItem[]>([]);
  const [isChildSearching, setIsChildSearching] = useState(false);
  const [childSearchError, setChildSearchError] = useState('');
  const [kindergartenKeyword, setKindergartenKeyword] = useState('');
  const [selectedKindergarten, setSelectedKindergarten] = useState<KindergartenLookupItem | null>(null);
  const [isKindergartenPopupOpen, setIsKindergartenPopupOpen] = useState(false);
  const [kindergartenSearchKeyword, setKindergartenSearchKeyword] = useState('');
  const [kindergartenSearchResults, setKindergartenSearchResults] = useState<KindergartenLookupItem[]>([]);
  const [isKindergartenSearching, setIsKindergartenSearching] = useState(false);
  const [kindergartenSearchError, setKindergartenSearchError] = useState('');

  const [rrnFirst6, setRrnFirst6] = useState('');
  const [rrnBack7, setRrnBack7] = useState('');
  const [gender, setGender] = useState<GenderChoice>('');
  const [isPrimaryGuardian, setIsPrimaryGuardian] = useState(false);
  const [relationship, setRelationship] = useState('');
  const [customRelationship, setCustomRelationship] = useState('');

  const [genderOptions, setGenderOptions] = useState<CommonCodeItem[]>(FALLBACK_GENDER_OPTIONS);
  const [relationshipOptions, setRelationshipOptions] = useState<CommonCodeItem[]>(FALLBACK_RELATIONSHIP_OPTIONS);
  const [teacherLevelOptions, setTeacherLevelOptions] = useState<CommonCodeItem[]>(FALLBACK_TEACHER_LEVEL_OPTIONS);
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<FieldErrorKey, string>>>({});
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
    const commonValid = !!(
      form.name.trim() &&
      form.loginId.trim() &&
      form.password.trim() &&
      form.confirmPassword.trim() &&
      form.email.trim() &&
      form.phone.trim() &&
      agreeTerms
    );

    if (memberType === 'GUARDIAN') {
      return !!(
        commonValid &&
        rrnFirst6.length === 6 &&
        rrnBack7.length === 7 &&
        gender &&
        relationship &&
        (relationship !== 'OTHER' || customRelationship.trim()) &&
        selectedChild
      );
    }

    if (memberType === 'SUPERADMIN') {
      return commonValid && !!form.department.trim();
    }

    if (memberType === 'KINDERGARTEN') {
      const startDate = form.startDate.trim();
      const endDate = form.endDate.trim();
      const endDateValid = !endDate || endDate >= startDate;
      return !!(
        commonValid &&
        selectedKindergarten &&
        form.staffNo.trim() &&
        form.level.trim() &&
        form.emergencyContactName.trim() &&
        form.emergencyContactPhone.trim() &&
        rrnFirst6.length === 6 &&
        rrnBack7.length === 7 &&
        startDate &&
        endDateValid
      );
    }

    return commonValid;
  }, [form, memberType, rrnFirst6, rrnBack7, relationship, customRelationship, selectedChild, selectedKindergarten, agreeTerms]);

  const onChange = (key: keyof typeof form, value: string) => {
    const nextValue = key === 'loginId' ? normalizeLoginId(value) : value;
    setForm((prev) => ({ ...prev, [key]: nextValue }));
    setFieldErrors((prev) => ({ ...prev, [key]: '' }));
    setError('');
    if (key === 'phone') {
      setIsCodeSent(false);
      setIsVerified(false);
      setVerificationCode('');
      setVerificationMessage('');
    }
  };

  const handleMemberTypeChange = (nextType: MemberType) => {
    if (memberType === nextType) return;

    setMemberType(nextType);
    setForm(INITIAL_FORM_STATE);
    setFieldErrors({});
    setError('');
    setAgreeTerms(false);

    setVerificationCode('');
    setIsCodeSent(false);
    setIsVerifying(false);
    setIsVerified(false);
    setVerificationMessage('');

    setChildNameKeyword('');
    setSelectedChild(null);
    setIsChildPopupOpen(false);
    setChildSearchFirst6('');
    setChildSearchBack7('');
    setChildSearchResults([]);
    setIsChildSearching(false);
    setChildSearchError('');

    setKindergartenKeyword('');
    setSelectedKindergarten(null);
    setIsKindergartenPopupOpen(false);
    setKindergartenSearchKeyword('');
    setKindergartenSearchResults([]);
    setIsKindergartenSearching(false);
    setKindergartenSearchError('');

    setRrnFirst6('');
    setRrnBack7('');
    setGender('');
    setIsPrimaryGuardian(false);
    setRelationship('');
    setCustomRelationship('');
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
        const [genderCodes, relationshipCodes, teacherLevelCodes] = await Promise.all([
          fetchCommonCodes('GENDER'),
          fetchCommonCodes('GUARDIAN_RELATIONSHIP'),
          fetchCommonCodes('teachers'),
        ]);
        if (genderCodes.length > 0) setGenderOptions(genderCodes);
        if (relationshipCodes.length > 0) setRelationshipOptions(relationshipCodes);
        if (teacherLevelCodes.length > 0) setTeacherLevelOptions(teacherLevelCodes);
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

  useEffect(() => {
    if (memberType !== 'KINDERGARTEN') return;

    const startDate = form.startDate.trim();
    const endDate = form.endDate.trim();

    // 두 날짜가 모두 입력된 경우에만 관계 유효성 체크
    if (!startDate || !endDate) {
      setFieldErrors((prev) => {
        if (prev.endDate !== DATE_RANGE_ERROR_MESSAGE) return prev;
        return { ...prev, endDate: '' };
      });
      setError((prev) => (prev === DATE_RANGE_GUIDE_MESSAGE ? '' : prev));
      return;
    }

    if (endDate < startDate) {
      setFieldErrors((prev) => ({ ...prev, endDate: DATE_RANGE_ERROR_MESSAGE }));
      setError(DATE_RANGE_GUIDE_MESSAGE);
      return;
    }

    setFieldErrors((prev) => {
      if (prev.endDate !== DATE_RANGE_ERROR_MESSAGE) return prev;
      return { ...prev, endDate: '' };
    });
    setError((prev) => (prev === DATE_RANGE_GUIDE_MESSAGE ? '' : prev));
  }, [memberType, form.startDate, form.endDate]);

  const searchChildren = async (first6Input: string, back7Input: string) => {
    const first6 = first6Input.replace(/\D/g, '').slice(0, 6);
    const back7 = back7Input.replace(/\D/g, '').slice(0, 7);

    if (!first6 || !back7) {
      setChildSearchError('주민등록번호 앞6자리와 뒷7자리를 모두 입력해주세요.');
      setChildSearchResults([]);
      return;
    }

    if (first6.length !== 6 || back7.length !== 7) {
      setChildSearchError('형식이 올바르지 않습니다. 앞6자리 / 뒷7자리 숫자로 입력해주세요.');
      setChildSearchResults([]);
      return;
    }
    const rrnKeyword = `${first6}-${back7}`;

    setChildSearchError('');
    setIsChildSearching(true);
    try {
      const encodedKeyword = encodeURIComponent(rrnKeyword);
      const candidateUrls = [
        `${API_BASE_URL}/children?keyword=${encodedKeyword}`,
        `${API_BASE_URL}/auth/children?keyword=${encodedKeyword}`,
        `${LEGACY_API_BASE_URL}/auth/children?keyword=${encodedKeyword}`,
      ];

      let data: ChildLookupItem[] | null = null;
      let lastStatus: number | null = null;

      for (const url of candidateUrls) {
        const response = await fetch(url, {
          method: 'GET',
          headers: { Accept: 'application/json' },
        });

        if (!response.ok) {
          lastStatus = response.status;
          continue;
        }

        const payload = await response.json();
        const rawItems: ChildApiItem[] = Array.isArray(payload)
          ? payload
          : Array.isArray(payload?.content)
            ? payload.content
            : [];

        data = rawItems
          .map((item) => {
            const childId = Number(item.childId ?? item.id ?? 0);
            const kindergartenId = Number(item.kindergartenId ?? item.kindergarten?.id ?? 0);
            const classId =
              item.classId !== undefined
                ? item.classId
                : (item.class?.id ?? null);
            const className =
              item.className !== undefined
                ? item.className
                : (item.class?.name ?? null);

            return {
              childId,
              kindergartenId,
              classId,
              className,
              name: item.name ?? '',
              childNo: item.childNo ?? null,
              birthDate: item.birthDate ?? null,
              gender: item.gender ?? null,
            } satisfies ChildLookupItem;
          })
          .filter((item) => item.childId > 0 && item.name);
        break;
      }

      if (!data) {
        if (lastStatus === 401) {
          throw new Error('아이 조회 권한이 없습니다. 백엔드 공개 조회 경로를 확인해주세요.');
        }
        throw new Error('아이 조회에 실패했습니다.');
      }

      setChildSearchResults(data);
      if (data.length === 0) setChildSearchError('검색 결과가 없습니다.');
    } catch (err) {
      setChildSearchResults([]);
      setChildSearchError(err instanceof Error ? err.message : '아이 조회에 실패했습니다.');
    } finally {
      setIsChildSearching(false);
    }
  };

  const searchKindergartens = async (keyword: string) => {
    const trimmed = keyword.trim();
    if (!trimmed) {
      setKindergartenSearchError('유치원명 또는 사업자번호를 입력해주세요.');
      setKindergartenSearchResults([]);
      return;
    }

    setKindergartenSearchError('');
    setIsKindergartenSearching(true);
    try {
      const response = await fetch(`${API_BASE_URL}/auth/kindergartens?keyword=${encodeURIComponent(trimmed)}`, {
        method: 'GET',
        headers: { Accept: 'application/json' },
      });
      if (!response.ok) {
        throw new Error('유치원 조회에 실패했습니다.');
      }
      const data = (await response.json()) as KindergartenLookupItem[];
      setKindergartenSearchResults(data);
      if (data.length === 0) setKindergartenSearchError('검색 결과가 없습니다.');
    } catch (err) {
      setKindergartenSearchResults([]);
      setKindergartenSearchError(err instanceof Error ? err.message : '유치원 조회에 실패했습니다.');
    } finally {
      setIsKindergartenSearching(false);
    }
  };

  const openChildPopup = () => {
    setChildSearchResults([]);
    setChildSearchError('');
    setIsChildPopupOpen(true);
    if (childSearchFirst6.length === 6 && childSearchBack7.length === 7) {
      void searchChildren(childSearchFirst6, childSearchBack7);
    }
  };

  const selectChild = (child: ChildLookupItem) => {
    setSelectedChild(child);
    setChildNameKeyword(`${childSearchFirst6}-${childSearchBack7}`);
    setIsChildPopupOpen(false);
  };

  const openKindergartenPopup = () => {
    const keyword = kindergartenKeyword.trim();
    setKindergartenSearchKeyword(keyword);
    setKindergartenSearchResults([]);
    setKindergartenSearchError('');
    setIsKindergartenPopupOpen(true);
    if (keyword) void searchKindergartens(keyword);
  };

  const selectKindergarten = (kindergarten: KindergartenLookupItem) => {
    setSelectedKindergarten(kindergarten);
    setKindergartenKeyword(kindergarten.name);
    setIsKindergartenPopupOpen(false);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});

    if (!form.name.trim()) {
      setFieldErrors({ name: '이름을 입력해주세요.' });
      setError('이름을 입력해주세요.');
      return;
    }
    if (!form.loginId.trim()) {
      setFieldErrors({ loginId: '로그인 ID를 입력해주세요.' });
      setError('로그인 ID를 입력해주세요.');
      return;
    }
    if (!form.email.trim()) {
      setFieldErrors({ email: '이메일을 입력해주세요.' });
      setError('이메일을 입력해주세요.');
      return;
    }
    if (!form.phone.trim()) {
      setFieldErrors({ phone: '전화번호를 입력해주세요.' });
      setError('전화번호를 입력해주세요.');
      return;
    }
    if (!form.password.trim()) {
      setFieldErrors({ password: '비밀번호를 입력해주세요.' });
      setError('비밀번호를 입력해주세요.');
      return;
    }
    if (!form.confirmPassword.trim()) {
      setFieldErrors({ confirmPassword: '비밀번호 확인을 입력해주세요.' });
      setError('비밀번호 확인을 입력해주세요.');
      return;
    }
    if (!agreeTerms) {
      setFieldErrors({ agreeTerms: '서비스 이용약관 및 개인정보 처리방침 동의가 필요합니다.' });
      setError('서비스 이용약관 및 개인정보 처리방침 동의가 필요합니다.');
      return;
    }

    if (form.password !== form.confirmPassword) {
      setFieldErrors({ confirmPassword: '비밀번호와 비밀번호 확인이 일치하지 않습니다.' });
      return;
    }

    if (memberType === 'GUARDIAN') {
      if (!selectedChild) {
        setFieldErrors({ child: '아이 찾기에서 아이를 선택해주세요.' });
        setError('아이 찾기에서 아이를 선택해주세요.');
        return;
      }
      if (rrnFirst6.length !== 6 || rrnBack7.length !== 7) {
        setFieldErrors({ rrn: '주민등록번호를 정확히 입력해주세요.' });
        setError('주민등록번호를 정확히 입력해주세요.');
        return;
      }
      if (!relationship) {
        setFieldErrors({ relationship: '관계를 선택해주세요.' });
        setError('관계를 선택해주세요.');
        return;
      }
      if (relationship === 'OTHER' && !customRelationship.trim()) {
        setFieldErrors({ relationship: '기타 관계를 입력해주세요.' });
        setError('기타 관계를 입력해주세요.');
        return;
      }
    }

    if (memberType === 'SUPERADMIN' && !form.department.trim()) {
      setFieldErrors({ department: '행정청 부서명을 입력해주세요.' });
      setError('행정청 부서명을 입력해주세요.');
      return;
    }

    if (memberType === 'KINDERGARTEN') {
      if (!selectedKindergarten) {
        setFieldErrors({ kindergarten: '유치원 찾기에서 유치원을 선택해주세요.' });
        setError('유치원 찾기에서 유치원을 선택해주세요.');
        return;
      }
      if (!form.staffNo.trim()) {
        setFieldErrors({ staffNo: '직원(사번)을 입력해주세요.' });
        setError('직원(사번)을 입력해주세요.');
        return;
      }
      if (rrnFirst6.length !== 6 || rrnBack7.length !== 7) {
        setFieldErrors({ rrn: '주민등록번호를 정확히 입력해주세요.' });
        setError('주민등록번호를 정확히 입력해주세요.');
        return;
      }
      if (!form.emergencyContactName.trim()) {
        setFieldErrors({ emergencyContactName: '비상 연락처 이름을 입력해주세요.' });
        setError('비상 연락처 이름을 입력해주세요.');
        return;
      }
      if (!form.emergencyContactPhone.trim()) {
        setFieldErrors({ emergencyContactPhone: '비상 연락처 전화번호를 입력해주세요.' });
        setError('비상 연락처 전화번호를 입력해주세요.');
        return;
      }
      if (!form.level.trim()) {
        setFieldErrors({ level: '직급을 선택해주세요.' });
        setError('직급을 선택해주세요.');
        return;
      }
      if (!form.startDate.trim()) {
        setFieldErrors({ startDate: '근무시작일을 입력해주세요.' });
        setError('근무시작일을 입력해주세요.');
        return;
      }
      if (form.endDate.trim() && form.endDate < form.startDate) {
        setFieldErrors({ endDate: DATE_RANGE_ERROR_MESSAGE });
        setError(DATE_RANGE_GUIDE_MESSAGE);
        return;
      }
    }

    if (
      memberType !== 'GUARDIAN' &&
      memberType !== 'KINDERGARTEN' &&
      memberType !== 'SUPERADMIN' &&
      memberType !== 'PLATFORM_IT_ADMIN'
    ) {
      setError('선택한 회원유형은 아직 회원가입을 지원하지 않습니다.');
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
          childId: memberType === 'GUARDIAN' ? selectedChild?.childId : null,
          rrnFirst6: memberType === 'GUARDIAN' || memberType === 'KINDERGARTEN' ? rrnFirst6 : null,
          rrnBack7: memberType === 'GUARDIAN' || memberType === 'KINDERGARTEN' ? rrnBack7 : null,
          relationship: memberType === 'GUARDIAN' ? relationship : null,
          customRelationship: memberType === 'GUARDIAN' && relationship === 'OTHER' ? customRelationship.trim() : '',
          primaryGuardian: memberType === 'GUARDIAN' ? isPrimaryGuardian : false,
          department: memberType === 'SUPERADMIN' ? form.department.trim() : null,
          kindergartenId: memberType === 'KINDERGARTEN' ? selectedKindergarten?.kindergartenId : null,
          staffNo: memberType === 'KINDERGARTEN' ? form.staffNo.trim() : null,
          gender: memberType === 'KINDERGARTEN' ? gender : null,
          emergencyContactName: memberType === 'KINDERGARTEN' ? form.emergencyContactName.trim() : null,
          emergencyContactPhone: memberType === 'KINDERGARTEN' ? form.emergencyContactPhone.trim() : null,
          level: memberType === 'KINDERGARTEN' ? form.level : null,
          startDate: memberType === 'KINDERGARTEN' ? form.startDate : null,
          endDate: memberType === 'KINDERGARTEN' ? (form.endDate || null) : null,
        }),
      });

      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        const backendError = data.error || '회원가입에 실패했습니다.';
        mapBackendErrorToField(backendError, setFieldErrors);
        throw new Error(backendError);
      }

      // 회원가입 직후 자동 로그인 처리 (로그인 상태 유지)
      const loginResponse = await loginApi({
        identifier: form.loginId,
        password: form.password,
      }).unwrap();

      const responseLoginId = loginResponse?.loginId ?? form.loginId;
      const role = loginResponse?.role ?? 'guardian';
      const token = loginResponse?.accessToken ?? loginResponse?.token ?? '';
      const name = loginResponse?.name;
      const user = {
        id: responseLoginId,
        username: responseLoginId,
        name: name || responseLoginId,
        role: mapBackendRoleToFrontendRole(role),
      };

      dispatch(setCredentials({ user, token }));
      router.push('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : '회원가입에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return {
    form, onChange, memberType, handleMemberTypeChange,
    verificationCode, setVerificationCode, isCodeSent, isVerifying, isVerified, verificationMessage,
    handleSendVerificationCode, handleVerifyCode,
    childNameKeyword, setChildNameKeyword, selectedChild, isChildPopupOpen, setIsChildPopupOpen,
    childSearchFirst6, setChildSearchFirst6, childSearchBack7, setChildSearchBack7, childSearchResults, isChildSearching, childSearchError,
    searchChildren, openChildPopup, selectChild,
    kindergartenKeyword, setKindergartenKeyword, selectedKindergarten, isKindergartenPopupOpen, setIsKindergartenPopupOpen,
    kindergartenSearchKeyword, setKindergartenSearchKeyword, kindergartenSearchResults, isKindergartenSearching, kindergartenSearchError,
    searchKindergartens, openKindergartenPopup, selectKindergarten,
    rrnFirst6, setRrnFirst6, rrnBack7, onRrnBack7Change, gender, genderOptions,
    teacherLevelOptions,
    isPrimaryGuardian, setIsPrimaryGuardian, relationship, setRelationship, customRelationship, setCustomRelationship,
    filteredRelationshipOptions, agreeTerms, setAgreeTerms, error, fieldErrors, isSubmitting, isValid, handleSubmit
  };
}

function mapBackendErrorToField(
  backendMessage: string,
  setFieldErrors?: React.Dispatch<React.SetStateAction<Partial<Record<FieldErrorKey, string>>>>
) {
  if (!setFieldErrors) return;
  if (!backendMessage) return;

  if (backendMessage.includes('전화번호') && backendMessage.includes('중복')) {
    setFieldErrors((prev) => ({ ...prev, phone: backendMessage }));
    return;
  }
  if (backendMessage.includes('로그인 ID') && backendMessage.includes('중복')) {
    setFieldErrors((prev) => ({ ...prev, loginId: backendMessage }));
    return;
  }
  if (backendMessage.includes('이메일') && backendMessage.includes('중복')) {
    setFieldErrors((prev) => ({ ...prev, email: backendMessage }));
  }
}