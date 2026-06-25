import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchRegisterFieldAvailability, type RegisterRequest } from '@/services/apis/auth.api';
import { API_BASE_URL } from '@/config/api';
import { apiClient } from '@/services/apis/apiClient';

type MemberType = 'GUARDIAN' | 'KINDERGARTEN' | 'SUPERADMIN';
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
  regionCode: string | null;
  status: string | null;
};

/**
 * ADR-0013: 코드 목록은 백엔드 enum 엔드포인트(`/enums/{name}`)에서, 라벨(한글)은
 * 프론트엔드 i18n 에서 공급한다. 아래 상수가 그 i18n(코드→라벨 + 성별 분류) 원본이며,
 * enum 엔드포인트가 비거나 실패해도 그대로 폴백으로 쓰인다.
 */
const FALLBACK_GENDER_OPTIONS: CommonCodeItem[] = [
  { codeGroup: 'GENDER', parentCode: null, code: 'MALE', codeName: '남자', sortOrder: 1 },
  { codeGroup: 'GENDER', parentCode: null, code: 'FEMALE', codeName: '여자', sortOrder: 2 },
];

const FALLBACK_RELATIONSHIP_OPTIONS: CommonCodeItem[] = [
  { codeGroup: 'GUARDIAN_RELATIONSHIP', parentCode: 'FEMALE', code: 'MOTHER', codeName: '엄마', sortOrder: 1 },
  { codeGroup: 'GUARDIAN_RELATIONSHIP', parentCode: 'MALE', code: 'FATHER', codeName: '아빠', sortOrder: 2 },
  // OTHER has no parentCode → shown regardless of gender (filtered in filteredRelationshipOptions)
  { codeGroup: 'GUARDIAN_RELATIONSHIP', parentCode: null, code: 'OTHER', codeName: '기타', sortOrder: 3 },
];

/** DB `level_enum` 및 백엔드 `LevelEnum`과 동일한 코드 */
const FALLBACK_TEACHER_LEVEL_OPTIONS: CommonCodeItem[] = [
  { codeGroup: 'teachers', parentCode: 'level', code: 'DIRECTOR', codeName: '원장', sortOrder: 1 },
  { codeGroup: 'teachers', parentCode: 'level', code: 'VICE_DIRECTOR', codeName: '부원장', sortOrder: 2 },
  { codeGroup: 'teachers', parentCode: 'level', code: 'TEACHER', codeName: '일반교사', sortOrder: 3 },
];

/** ADR-0013 enum 엔드포인트 응답 항목: 코드 + 안정 정렬 인덱스(라벨 없음). */
type EnumValue = { code: string; sortOrder?: number };

/**
 * enum 엔드포인트의 코드 목록을 i18n 라벨 테이블(`fallback`)과 병합한다.
 * - 라벨/부모코드(parentCode)는 fallback 에서 코드로 매칭해 채운다(i18n 책임).
 * - i18n 라벨이 없는 코드는 가입 드롭다운에서 제외한다. (예: `teacher_level` 의 `OTHER`
 *   는 `LevelEnum` 에는 있으나 가입 직급 선택지가 아니므로 노출하지 않음 — 기존 동작 유지.)
 * - enum 응답이 비거나 라벨 매칭이 하나도 없으면 fallback 전체를 그대로 사용.
 */
function mergeEnumWithLabels(codes: EnumValue[], fallback: CommonCodeItem[]): CommonCodeItem[] {
  if (codes.length === 0) return fallback;
  const byCode = new Map(fallback.map((item) => [item.code, item]));
  const merged = codes
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    .map((entry) => byCode.get(entry.code))
    .filter((item): item is CommonCodeItem => item != null);
  return merged.length > 0 ? merged : fallback;
}

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

type SignupFormState = typeof INITIAL_FORM_STATE;

/** SignupForm `useEffect` 스크롤 순서와 동일하게 유지 */
export const FIELD_ERROR_FOCUS_ORDER: FieldErrorKey[] = [
  'name',
  'loginId',
  'email',
  'phone',
  'password',
  'confirmPassword',
  'child',
  'kindergarten',
  'rrn',
  'relationship',
  'department',
  'staffNo',
  'level',
  'emergencyContactName',
  'emergencyContactPhone',
  'agreeTerms',
];

function firstFieldErrorMessage(errors: Partial<Record<FieldErrorKey, string>>): string {
  for (const key of FIELD_ERROR_FOCUS_ORDER) {
    const msg = errors[key];
    if (msg) return msg;
  }
  const first = Object.values(errors)[0];
  return first ?? '';
}

function focusFirstFieldError(errors: Partial<Record<FieldErrorKey, string>>) {
  const fieldNameMap: Partial<Record<FieldErrorKey, string>> = {
    child: 'childSearchFirst6',
    kindergarten: 'kindergartenBizPart1',
    rrn: 'rrnFirst6',
  };
  for (const key of FIELD_ERROR_FOCUS_ORDER) {
    if (!errors[key]) continue;
    const targetName = fieldNameMap[key] ?? key;
    const targetElement = document.querySelector(`[name="${targetName}"]`) as HTMLElement | null;
    targetElement?.focus();
    targetElement?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    break;
  }
}

function focusFirstDuplicateAccountField(fieldErrors: Partial<Record<FieldErrorKey, string>>) {
  const order: Array<'loginId' | 'email' | 'phone'> = ['loginId', 'email', 'phone'];
  for (const key of order) {
    if (!isDuplicateAccountFieldMessage(fieldErrors[key])) continue;
    const targetElement = document.querySelector(`[name="${key}"]`) as HTMLElement | null;
    targetElement?.focus();
    targetElement?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    break;
  }
}

function buildSignupFieldErrors(args: {
  form: SignupFormState;
  memberType: MemberType;
  agreeTerms: boolean;
  isChildVerified: boolean;
  selectedKindergarten: KindergartenLookupItem | null;
  rrnFirst6: string;
  rrnBack7: string;
  relationship: string;
  customRelationship: string;
}): Partial<Record<FieldErrorKey, string>> {
  const {
    form,
    memberType,
    agreeTerms,
    isChildVerified,
    selectedKindergarten,
    rrnFirst6,
    rrnBack7,
    relationship,
    customRelationship,
  } = args;
  const e: Partial<Record<FieldErrorKey, string>> = {};

  if (!form.name.trim()) e.name = '이름을 입력해주세요.';
  if (!form.loginId.trim()) e.loginId = '로그인 ID를 입력해주세요.';
  if (!form.email.trim()) e.email = '이메일을 입력해주세요.';
  if (!form.phone.trim()) {
    e.phone = '전화번호를 입력해주세요.';
  } else {
    const phoneDigits = digitsOnlyPhone(form.phone);
    if (phoneDigits.length < 10 || phoneDigits.length > 11) {
      e.phone = '전화번호를 10~11자리 숫자로 입력해주세요.';
    }
  }
  if (!form.password.trim()) e.password = '비밀번호를 입력해주세요.';
  if (!form.confirmPassword.trim()) e.confirmPassword = '비밀번호 확인을 입력해주세요.';
  if (!agreeTerms) e.agreeTerms = '서비스 이용약관 및 개인정보 처리방침 동의가 필요합니다.';

  if (form.password.trim() && form.confirmPassword.trim() && form.password !== form.confirmPassword) {
    e.confirmPassword = '비밀번호와 비밀번호 확인이 일치하지 않습니다.';
  }

  if (memberType === 'GUARDIAN') {
    if (!isChildVerified) e.child = '아이 주민등록번호를 먼저 확인해주세요.';
    if (rrnFirst6.length !== 6 || rrnBack7.length !== 7) e.rrn = '주민등록번호를 정확히 입력해주세요.';
    if (!relationship) e.relationship = '관계를 선택해주세요.';
    else if (relationship === 'OTHER' && !customRelationship.trim()) {
      e.relationship = '기타 관계를 입력해주세요.';
    }
  }

  if (memberType === 'SUPERADMIN' && !form.department.trim()) {
    e.department = '행정청 부서명을 입력해주세요.';
  }

  if (memberType === 'KINDERGARTEN') {
    if (!selectedKindergarten) e.kindergarten = '유치원 찾기에서 유치원을 선택해주세요.';
    if (!form.staffNo.trim()) e.staffNo = '직원(사번)을 입력해주세요.';
    if (rrnFirst6.length !== 6 || rrnBack7.length !== 7) e.rrn = '주민등록번호를 정확히 입력해주세요.';
    if (!form.emergencyContactName.trim()) e.emergencyContactName = '비상 연락처 이름을 입력해주세요.';
    if (!form.emergencyContactPhone.trim()) e.emergencyContactPhone = '비상 연락처 전화번호를 입력해주세요.';
    if (!form.level.trim()) e.level = '직급을 선택해주세요.';
  }

  return e;
}

async function fetchAccountFieldDuplicatesOnSubmit(
  form: SignupFormState
): Promise<Partial<Record<FieldErrorKey, string>>> {
  const out: Partial<Record<FieldErrorKey, string>> = {};
  const loginId = form.loginId.trim();
  const email = form.email.trim();
  const phoneDigits = digitsOnlyPhone(form.phone);

  const tasks: Promise<void>[] = [];

  if (loginId.length >= 2) {
    tasks.push(
      fetchRegisterFieldAvailability('loginId', loginId)
        .then(({ available, message }) => {
          if (!available) out.loginId = message ?? '이미 사용 중인 로그인 ID입니다.';
        })
        .catch(() => {})
    );
  }
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    tasks.push(
      fetchRegisterFieldAvailability('email', email)
        .then(({ available, message }) => {
          if (!available) out.email = message ?? '이미 사용 중인 이메일입니다.';
        })
        .catch(() => {})
    );
  }
  if (phoneDigits.length >= 10 && phoneDigits.length <= 11) {
    tasks.push(
      fetchRegisterFieldAvailability('phone', phoneDigits)
        .then(({ available, message }) => {
          if (!available) {
            out.phone =
              message && (message.includes('이미') || message.includes('중복'))
                ? message
                : '이미 등록된 연락처(전화번호)입니다.';
          }
        })
        .catch(() => {})
    );
  }

  await Promise.all(tasks);
  return out;
}

/** 원장/부원장 → 백엔드 KINDERGARTEN_ADMIN, 그 외 직급 → TEACHER 역할 */
const mapKindergartenSignupUserRole = (levelCode: string): 'KINDERGARTEN_ADMIN' | 'TEACHER' =>
  levelCode === 'DIRECTOR' ||
  levelCode === 'VICE_DIRECTOR' ||
  levelCode === 'PRINCIPAL' ||
  levelCode === 'VICE_PRINCIPAL'
    ? 'KINDERGARTEN_ADMIN'
    : 'TEACHER';

export function useSignupForm() {
  const [form, setForm] = useState(INITIAL_FORM_STATE);

  const [memberType, setMemberType] = useState<MemberType>('GUARDIAN');
  const [childSearchFirst6, setChildSearchFirst6] = useState('');
  const [childSearchBack7, setChildSearchBack7] = useState('');
  const [isChildVerified, setIsChildVerified] = useState(false);
  const [isChildVerifying, setIsChildVerifying] = useState(false);
  const [childVerificationMessage, setChildVerificationMessage] = useState('');
  /** 사업자등록번호 10자리 — 화면은 3-2-5 분할 입력 (예: 599-91-66110 ) */
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
  const [successMessage, setSuccessMessage] = useState('');
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
        isChildVerified
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
    gender,
    relationship,
    customRelationship,
    isChildVerified,
    selectedKindergarten,
    agreeTerms,
    accountDuplicateBlocked,
  ]);

  const onChange = (key: keyof typeof form, value: string) => {
    const nextValue = key === 'loginId' ? normalizeLoginId(value) : value;
    setForm((prev) => ({ ...prev, [key]: nextValue }));
    setFieldErrors((prev) => ({ ...prev, [key]: '' }));
    setError('');
    setSuccessMessage('');
  };

  const handleMemberTypeChange = (nextType: MemberType) => {
    if (memberType === nextType) return;

    setMemberType(nextType);
    setForm(INITIAL_FORM_STATE);
    setFieldErrors({});
    setError('');
    setSuccessMessage('');
    setAgreeTerms(false);

    setChildSearchFirst6('');
    setChildSearchBack7('');
    setIsChildVerified(false);
    setIsChildVerifying(false);
    setChildVerificationMessage('');

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
    // ADR-0013: 코드는 enum 엔드포인트에서, 라벨은 프론트엔드 i18n(FALLBACK_*)에서.
    const fetchEnumCodes = async (name: string): Promise<EnumValue[]> => {
      const response = await fetch(`${API_BASE_URL}/enums/${encodeURIComponent(name)}`);
      if (!response.ok) throw new Error(`enum 조회 실패: ${name}`);
      const data = (await response.json()) as EnumValue[];
      return Array.isArray(data) ? data : [];
    };

    const loadEnumOptions = async () => {
      try {
        const [genderCodes, relationshipCodes, teacherLevelCodes] = await Promise.all([
          fetchEnumCodes('gender'),
          fetchEnumCodes('guardian_relationship'),
          fetchEnumCodes('teacher_level'),
        ]);
        setGenderOptions(mergeEnumWithLabels(genderCodes, FALLBACK_GENDER_OPTIONS));
        setRelationshipOptions(mergeEnumWithLabels(relationshipCodes, FALLBACK_RELATIONSHIP_OPTIONS));
        setTeacherLevelOptions(
          normalizeTeacherLevelOptions(mergeEnumWithLabels(teacherLevelCodes, FALLBACK_TEACHER_LEVEL_OPTIONS))
        );
      } catch {
        // enum 엔드포인트 실패 시 FALLBACK_* 초기값 유지
      }
    };

    void loadEnumOptions();
  }, []);

  useEffect(() => {
    const exists = filteredRelationshipOptions.some((option) => option.code === relationship);
    if (relationship && !exists) {
      setRelationship('');
      setCustomRelationship('');
    }
  }, [filteredRelationshipOptions, relationship]);

  const verifyChild = async (first6Input: string, back7Input: string) => {
    const first6 = first6Input.replace(/\D/g, '').slice(0, 6);
    const back7 = back7Input.replace(/\D/g, '').slice(0, 7);

    if (!first6 || !back7) {
      setChildVerificationMessage('주민등록번호 앞6자리와 뒷7자리를 모두 입력해주세요.');
      setIsChildVerified(false);
      return;
    }

    if (first6.length !== 6 || back7.length !== 7) {
      setChildVerificationMessage('형식이 올바르지 않습니다. 앞6자리 / 뒷7자리 숫자로 입력해주세요.');
      setIsChildVerified(false);
      return;
    }
    setChildVerificationMessage('');
    setIsChildVerifying(true);
    try {
      const { data: payload } = await apiClient.post<{ verified?: boolean }>(
        '/auth/guardian-child-verifications',
        { childRrnFirst6: first6, childRrnBack7: back7 }
      );
      const verified = payload.verified === true;
      setIsChildVerified(verified);
      setChildVerificationMessage(
        verified ? '아이 정보가 확인되었습니다.' : '입력한 정보와 일치하는 아이를 확인할 수 없습니다.'
      );
    } catch (err) {
      setIsChildVerified(false);
      const message =
        err != null && typeof err === 'object' && 'response' in err
          ? (err as { response?: { data?: { error?: string } } }).response?.data?.error
          : undefined;
      setChildVerificationMessage(message ?? (err instanceof Error ? err.message : '아이 정보 확인에 실패했습니다.'));
    } finally {
      setIsChildVerifying(false);
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

  const updateChildSearchFirst6 = (value: string) => {
    setChildSearchFirst6(value);
    setIsChildVerified(false);
    setChildVerificationMessage('');
  };

  const updateChildSearchBack7 = (value: string) => {
    setChildSearchBack7(value);
    setIsChildVerified(false);
    setChildVerificationMessage('');
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
      queueMicrotask(() => focusFirstDuplicateAccountField(fieldErrors));
      return;
    }

    const clientErrors = buildSignupFieldErrors({
      form,
      memberType,
      agreeTerms,
      isChildVerified,
      selectedKindergarten,
      rrnFirst6,
      rrnBack7,
      relationship,
      customRelationship,
    });

    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors);
      setError(firstFieldErrorMessage(clientErrors));
      queueMicrotask(() => focusFirstFieldError(clientErrors));
      return;
    }

    setError('');
    setFieldErrors({});

    const duplicateOnSubmit = await fetchAccountFieldDuplicatesOnSubmit(form);
    if (Object.keys(duplicateOnSubmit).length > 0) {
      setFieldErrors(duplicateOnSubmit);
      setError(firstFieldErrorMessage(duplicateOnSubmit));
      queueMicrotask(() => focusFirstFieldError(duplicateOnSubmit));
      return;
    }

    if (
      memberType !== 'GUARDIAN' &&
      memberType !== 'KINDERGARTEN' &&
      memberType !== 'SUPERADMIN'
    ) {
      setError('선택한 회원유형은 아직 회원가입을 지원하지 않습니다.');
      return;
    }

    const phoneDigits = digitsOnlyPhone(form.phone);

    setIsSubmitting(true);
    try {
      let registerBody: RegisterRequest;

      if (memberType === 'GUARDIAN') {
        registerBody = {
          userRole: 'GUARDIAN',
          loginId: form.loginId,
          password: form.password,
          email: form.email,
          phone: phoneDigits,
          name: form.name.trim(),
          rrnFirst6,
          rrnBack7,
          gender,
          address: '',
          childRrnFirst6: childSearchFirst6,
          childRrnBack7: childSearchBack7,
          relationship,
          primaryGuardian: isPrimaryGuardian,
        };
      } else if (memberType === 'KINDERGARTEN') {
        registerBody = {
          userRole: mapKindergartenSignupUserRole(form.level),
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
          loginId: form.loginId,
          password: form.password,
          email: form.email,
          phone: phoneDigits,
          name: form.name.trim(),
          department: form.department.trim(),
        };
      } else {
        setError('선택한 회원유형은 아직 회원가입을 지원하지 않습니다.');
        setIsSubmitting(false);
        return;
      }

      await apiClient.post('/auth/register', registerBody).catch((err) => {
        const backendError =
          (err != null && typeof err === 'object' && 'response' in err
            ? (err as { response?: { data?: { error?: string } } }).response?.data?.error
            : undefined) ?? '회원가입에 실패했습니다.';
        mapBackendErrorToField(backendError, setFieldErrors);
        throw new Error(backendError);
      });

      setSuccessMessage('가입 신청이 제출되었습니다. 승인 완료 안내를 받은 뒤 로그인해 주세요.');
    } catch (err) {
      setError(err instanceof Error ? err.message : '회원가입에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return {
    form, onChange, memberType, handleMemberTypeChange,
    isChildVerified, isChildVerifying, childVerificationMessage,
    childSearchFirst6, setChildSearchFirst6: updateChildSearchFirst6,
    childSearchBack7, setChildSearchBack7: updateChildSearchBack7,
    verifyChild,
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
    filteredRelationshipOptions, agreeTerms, setAgreeTerms, error, successMessage, fieldErrors, isSubmitting, isValid, handleSubmit,
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
