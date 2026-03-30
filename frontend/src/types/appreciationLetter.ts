/**
 * DB `appreciation_target_type_enum` / 백엔드 `AppreciationTargetTypeEnum`
 */
export type AppreciationTargetType = 'KINDERGARTEN' | 'TEACHER';

/** DB `status_enum` — 감사 편지 `status` 컬럼 */
export type AppreciationLetterStatus = 'ACTIVE' | 'PENDING' | 'DISABLED';

/** 백엔드 `AppreciationLetterVO` (JSON camelCase) */
export type AppreciationLetterVO = {
  letterId: number;
  kindergartenId: number;
  senderUserId: number;
  targetType: string;
  targetId: number;
  title: string;
  content: string;
  isPublic: boolean;
  status: string;
  createdAt: string;
  updatedAt: string;
  /** API에 포함될 수 있는 표시용 필드(없을 수 있음). 상세에서 로그인 ID 보강 시 참고. */
  senderLoginId?: string;
  /** API 응답에 포함될 수 있는 작성자 이름(없을 수 있음). */
  senderGuardianName?: string;
};

export const APPRECIATION_LETTER_STATUS_OPTIONS: { value: AppreciationLetterStatus; label: string }[] = [
  { value: 'PENDING', label: '대기' },
  { value: 'ACTIVE', label: '게시' },
  { value: 'DISABLED', label: '비활성' },
];

export const APPRECIATION_TARGET_TYPE_OPTIONS: { value: AppreciationTargetType; label: string }[] = [
  { value: 'KINDERGARTEN', label: '유치원' },
  { value: 'TEACHER', label: '교사' },
];
