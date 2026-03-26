import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { fetchRegisterFieldAvailability, useLoginMutation } from '@/services/apis/auth.api';
import { useAppDispatch } from '@/store/hook';
import { setCredentials } from '@/store/slices/userSlice';
import type { UserRole } from '@/types/user-role';
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

/** DB `level_enum` 및 백엔드 `LevelEnum`과 동일한 코드 */
const FALLBACK_TEACHER_LEVEL_OPTIONS: CommonCodeItem[] = [
  { codeGroup: 'teachers', parentCode: 'level', code: 'DIRECTOR', codeName: '원장', sortOrder: 1 },
  { codeGroup: 'teachers', parentCode: 'level', code: 'VICE_DIRECTOR', codeName: '부원장', sortOrder: 2 },
  { codeGroup: 'teachers', parentCode: 'level', code: 'TEACHER', codeName: '일반교사', sortOrder: 3 },
];

const LEGACY_TEACHER_LEVEL_CODE_MAP: Record<string, string> = {
  PRINCIPAL: 'DIRECTOR',
  VICE_PRINCIPAL: 'VICE_DIRECTOR',
};

function normalizeTeacherLevelOptions(items: CommonCodeItem[]): CommonCodeItem[] {
  return items.map((row) => {
    const mapped = LEGACY_TEACHER_LEVEL_CODE_MAP[row.code];
    return mapped ? { ...row, code: mapped } : row;
  });
}

const normalizeLoginId = (value: string) => value.replace(/[^A-Za-z0-9]/g, '');

/** API·DB(숫자 10~11자리)용 — 화면 입력은 하이픈 허용, 전송 시에만 사용 */
const digitsOnlyPhone = (value: string) => value.replace(/\D/g, '');

function isDuplicateAccountFieldMessage(msg: string | undefined): boolean {
  if (!msg) return false;
  return msg.includes('이미 사용 중인') || msg.includes('이미 등록된 연락처');
}
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
};

/** 원장(DIRECTOR) → 백엔드 KINDERGARTEN_ADMIN, 그 외 직급 → TEACHER 역할 */
const mapKindergartenSignupUserRole = (levelCode: string): 'KINDERGARTEN_ADMIN' | 'TEACHER' =>
  levelCode === 'DIRECTOR' || levelCode === 'PRINCIPAL' ? 'KINDERGARTEN_ADMIN' : 'TEACHER';

export function auth() {
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
  /** 사업자등록번호 10자리 — 화면은 3-2-5 분할 입력 (예: 123-45-67890) */
  const [kindergartenBizPart1, setKindergartenBizPart1] = useState('');
  const [kindergartenBizPart2, setKindergartenBizPart2] = useState('');
  const [kindergartenBizPart3, setKindergartenBizPart3] = useState('');
  const [selectedKindergarten, setSelectedKindergarten] = useState<KindergartenLookupItem | null>(null);
  const [isKindergartenPopupOpen, setIsKindergartenPopupOpen] = useState(false);
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

  const accountDuplicateBlocked = useMemo(
    () =>
      isDuplicateAccountFieldMessage(fieldErrors.loginId) ||
      isDuplicateAccountFieldMessage(fieldErrors.email) ||
      isDuplicateAccountFieldMessage(fieldErrors.phone),
    [fieldErrors.loginId, fieldErrors.email, fieldErrors.phone]
  );

  const handleAccountFieldBlur = useCallback(async (field: 'loginId' | 'email' | 'phone', rawValue: string) => {
    const trimmed = rawValue.trim();
    if (!trimmed) {
      setFieldErrors((p) => ({ ...p, [field]: '' }));
      return;
    }
    if (field === 'loginId' && trimmed.length < 2) {
      return;
    }
    if (field === 'email') {
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
        setFieldErrors((p) => ({ ...p, email: '올바른 이메일 형식을 입력해주세요.' }));
        return;
      }
    }
    if (field === 'phone') {
      const digits = trimmed.replace(/\D/g, '');
      if (digits.length < 8) {
        return;
      }
    }
    try {
      const valueForApi = field === 'phone' ? digitsOnlyPhone(trimmed) : trimmed;
      const { available, message } = await fetchRegisterFieldAvailability(field, valueForApi);
      setFieldErrors((p) => ({
        ...p,
        [field]: available ? '' : (message ?? '이미 사용 중입니다.'),
      }));
      if (available) setError('');
    } catch {
      // 포커스 아웃 검사만 생략
    }
  }, []);

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

    if (accountDuplicateBlocked) {
      return false;
    }

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
      return !!(
        commonValid &&
        selectedKindergarten &&
        form.staffNo.trim() &&
        form.level.trim() &&
        form.emergencyContactName.trim() &&
        form.emergencyContactPhone.trim() &&
        rrnFirst6.length === 6 &&
        rrnBack7.length === 7
      );
    }

    return commonValid;
  }, [
    form,
    memberType,
    rrnFirst6,
    rrnBack7,
    relationship,
    customRelationship,
    selectedChild,
    selectedKindergarten,
    agreeTerms,
    accountDuplicateBlocked,
  ]);

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

    setKindergartenBizPart1('');
    setKindergartenBizPart2('');
    setKindergartenBizPart3('');
    setSelectedKindergarten(null);
    setIsKindergartenPopupOpen(false);
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
        body: JSON.stringify({ type: 'PHONE', target: digitsOnlyPhone(form.phone) }),
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
        body: JSON.stringify({ target: digitsOnlyPhone(form.phone), code: verificationCode }),
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
      const response = await fetch(`${API_BASE_URL}/common_codes/code_group/${encodeURIComponent(group)}`);
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
        if (teacherLevelCodes.length > 0) {
          setTeacherLevelOptions(normalizeTeacherLevelOptions(teacherLevelCodes));
        }
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
    setChildSearchError('');
    setIsChildSearching(true);
    try {
      const candidateUrls = [
        `${API_BASE_URL}/children/rrn?rrn_First6=${encodeURIComponent(first6)}&rrn_Last7=${encodeURIComponent(back7)}`,
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
          if (response.status === 401) {
            throw new Error('아이 조회 권한이 없습니다. 로그인 상태 또는 백엔드 권한 설정을 확인해주세요.');
          }
          continue;
        }

        const payload = await response.json();
        const rawItems: ChildApiItem[] = Array.isArray(payload)
          ? payload
          : Array.isArray(payload?.content)
            ? payload.content
            : (payload && typeof payload === 'object' ? [payload as ChildApiItem] : []);

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

  const getKindergartenBusinessDigits = () =>
    `${kindergartenBizPart1}${kindergartenBizPart2}${kindergartenBizPart3}`.replace(/\D/g, '');

  const searchKindergartens = async () => {
    const digits = getKindergartenBusinessDigits();
    if (digits.length !== 10) {
      setKindergartenSearchError('사업자등록번호 10자리를 입력해주세요. (숫자만, XXX-XX-XXXXX)');
      setKindergartenSearchResults([]);
      return;
    }

    setKindergartenSearchError('');
    setIsKindergartenSearching(true);
    try {
      const response = await fetch(
      `${API_BASE_URL}/kindergartens/business-registration-no/${encodeURIComponent(digits)}`,
        {
          method: 'GET',
          headers: { Accept: 'application/json' },
        }
      );
      if (!response.ok) {
        throw new Error('유치원 조회에 실패했습니다.');
      }
      const data = (await response.json()) as KindergartenLookupItem[];
      setKindergartenSearchResults(data);
      if (data.length === 0) setKindergartenSearchError('일치하는 유치원이 없습니다.');
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
    setKindergartenSearchResults([]);
    setKindergartenSearchError('');
    setIsKindergartenPopupOpen(true);
    const digits = getKindergartenBusinessDigits();
    if (digits.length === 10) void searchKindergartens();
  };

  const selectKindergarten = (kindergarten: KindergartenLookupItem) => {
    setSelectedKindergarten(kindergarten);
    const digits = (kindergarten.businessRegistrationNo ?? '').replace(/\D/g, '');
    if (digits.length === 10) {
      setKindergartenBizPart1(digits.slice(0, 3));
      setKindergartenBizPart2(digits.slice(3, 5));
      setKindergartenBizPart3(digits.slice(5, 10));
    }
    setIsKindergartenPopupOpen(false);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (
      isDuplicateAccountFieldMessage(fieldErrors.loginId) ||
      isDuplicateAccountFieldMessage(fieldErrors.email) ||
      isDuplicateAccountFieldMessage(fieldErrors.phone)
    ) {
      setError('로그인 ID·이메일·연락처 중복 안내를 확인한 뒤 수정해주세요.');
      return;
    }
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
    const phoneDigits = digitsOnlyPhone(form.phone);
    if (phoneDigits.length < 10 || phoneDigits.length > 11) {
      setFieldErrors({ phone: '전화번호를 10~11자리 숫자로 입력해주세요.' });
      setError('전화번호를 10~11자리 숫자로 입력해주세요.');
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
      // 백엔드 AuthRegisterRequest 스펙에 맞춤 (status 는 서버에서 ACTIVE 고정, 히든으로만 전송)
      let registerBody: Record<string, unknown>;

      if (memberType === 'GUARDIAN') {
        registerBody = {
          userRole: 'GUARDIAN',
          status: 'ACTIVE',
          loginId: form.loginId,
          password: form.password,
          email: form.email,
          phone: phoneDigits,
          name: form.name.trim(),
          rrnFirst6,
          rrnBack7,
          gender,
          address: '',
          kindergartenId: selectedChild!.kindergartenId,
          childId: selectedChild!.childId,
          childRrnFirst6: childSearchFirst6,
          childRrnBack7: childSearchBack7,
          relationship,
          primaryGuardian: isPrimaryGuardian,
        };
      } else if (memberType === 'KINDERGARTEN') {
        registerBody = {
          userRole: mapKindergartenSignupUserRole(form.level),
          status: 'ACTIVE',
          loginId: form.loginId,
          password: form.password,
          email: form.email,
          phone: phoneDigits,
          name: form.name.trim(),
          rrnFirst6,
          rrnBack7,
          gender,
          kindergartenId: selectedKindergarten!.kindergartenId,
          emergencyContactName: form.emergencyContactName.trim(),
          emergencyContactPhone: digitsOnlyPhone(form.emergencyContactPhone),
          level: form.level,
          staffNo: form.staffNo.trim(),
        };
      } else if (memberType === 'SUPERADMIN') {
        registerBody = {
          userRole: 'SUPERADMIN',
          status: 'ACTIVE',
          loginId: form.loginId,
          password: form.password,
          email: form.email,
          phone: phoneDigits,
          name: form.name.trim(),
          department: form.department.trim(),
        };
      } else if (memberType === 'PLATFORM_IT_ADMIN') {
        registerBody = {
          userRole: 'PLATFORM_IT_ADMIN',
          status: 'ACTIVE',
          loginId: form.loginId,
          password: form.password,
          email: form.email,
          phone: phoneDigits,
          name: form.name.trim(),
        };
      } else {
        setError('선택한 회원유형은 아직 회원가입을 지원하지 않습니다.');
        setIsSubmitting(false);
        return;
      }

      const response = await fetch(`${API_BASE_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(registerBody),
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
      const role = loginResponse?.role ?? 'GUARDIAN';
      const token = loginResponse?.accessToken ?? loginResponse?.token ?? '';
      const name = loginResponse?.name;
      const user = {
        id: responseLoginId,
        loginId: responseLoginId,
        username: responseLoginId,
        name: name || responseLoginId,
        role: role as UserRole,
      };

      dispatch(setCredentials({ user, token }));
      try {
        localStorage.setItem('user', JSON.stringify(user));
        if (token) {
          localStorage.setItem('token', token);
          localStorage.setItem('accessToken', token);
        }
      } catch {
        // 저장 실패 시에도 Redux 세션은 유지
      }
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
    kindergartenBizPart1,
    setKindergartenBizPart1,
    kindergartenBizPart2,
    setKindergartenBizPart2,
    kindergartenBizPart3,
    setKindergartenBizPart3,
    selectedKindergarten,
    isKindergartenPopupOpen,
    setIsKindergartenPopupOpen,
    kindergartenSearchResults,
    isKindergartenSearching,
    kindergartenSearchError,
    searchKindergartens,
    openKindergartenPopup,
    selectKindergarten,
    rrnFirst6, setRrnFirst6, rrnBack7, onRrnBack7Change, gender, genderOptions,
    teacherLevelOptions,
    isPrimaryGuardian, setIsPrimaryGuardian, relationship, setRelationship, customRelationship, setCustomRelationship,
    filteredRelationshipOptions, agreeTerms, setAgreeTerms, error, fieldErrors, isSubmitting, isValid, handleSubmit,
    handleAccountFieldBlur,
  };
}

function mapBackendErrorToField(
  backendMessage: string,
  setFieldErrors?: React.Dispatch<React.SetStateAction<Partial<Record<FieldErrorKey, string>>>>
) {
  if (!setFieldErrors) return;
  if (!backendMessage) return;

  const lower = backendMessage.toLowerCase();

  if (
    lower.includes('uq_user_account_phone') ||
    ((lower.includes('phone') || backendMessage.includes('전화번호') || backendMessage.includes('연락처')) &&
      (lower.includes('unique') || lower.includes('duplicate') || backendMessage.includes('중복')))
  ) {
    setFieldErrors((prev) => ({
      ...prev,
      phone: backendMessage.includes('중복') ? backendMessage : '이미 등록된 연락처(전화번호)입니다.',
    }));
    return;
  }
  if (
    lower.includes('uq_user_account_email') ||
    (lower.includes('email') && (lower.includes('unique') || lower.includes('duplicate'))) ||
    (backendMessage.includes('이메일') && backendMessage.includes('중복'))
  ) {
    setFieldErrors((prev) => ({
      ...prev,
      email: backendMessage.includes('중복') ? backendMessage : '이미 사용 중인 이메일입니다.',
    }));
    return;
  }
  if (
    lower.includes('users_login_id') ||
    (lower.includes('login_id') && (lower.includes('unique') || lower.includes('duplicate'))) ||
    (backendMessage.includes('로그인 ID') && backendMessage.includes('중복'))
  ) {
    setFieldErrors((prev) => ({
      ...prev,
      loginId: backendMessage.includes('중복') ? backendMessage : '이미 사용 중인 로그인 ID입니다.',
    }));
  }
}