/**
 * DB `appreciation_target_type_enum` / 백엔드 `AppreciationTargetTypeEnum`
 */
export type AppreciationTargetType = 'KINDERGARTEN' | 'TEACHER';

/** 백엔드 `AppreciationLetterVO` (JSON camelCase) */
export type AppreciationLetterVO = {
  letterId: number;
  senderName: string | null;
  targetType: string;
  targetName: string | null;
  title: string;
  content: string;
  isPublic: boolean;
  editable: boolean;
  createdAt: string;
  updatedAt: string;
};

export const APPRECIATION_TARGET_TYPE_OPTIONS: { value: AppreciationTargetType; label: string }[] = [
  { value: 'KINDERGARTEN', label: '유치원' },
  { value: 'TEACHER', label: '교사' },
];
