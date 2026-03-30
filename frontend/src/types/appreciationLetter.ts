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
