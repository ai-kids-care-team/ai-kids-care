'use client';

import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { getKindergarten, searchKindergartens, type KindergartenVO } from '@/services/apis/kindergartens.api';
import {
  normalizeTeacherVO,
  searchTeachers,
  type TeacherApiRow,
  type TeacherVO,
} from '@/services/apis/teachers.api';
import type { AppreciationTargetType } from '@/types/appreciationLetter';

const LIST_PAGE_SIZE = 100;
const TEACHER_FALLBACK_SIZE = 500;

function minimalKindergartenVO(kindergartenId: number, name: string): KindergartenVO {
  return {
    kindergartenId,
    name,
    address: null,
    regionCode: null,
    code: null,
    businessRegistrationNo: null,
    contactName: null,
    contactPhone: null,
    contactEmail: null,
    status: null,
    createdAt: null,
    updatedAt: null,
  };
}

type LetterTargetPickerProps = {
  targetType: AppreciationTargetType;
  onTargetTypeChange: (t: AppreciationTargetType) => void;
  onSelectKindergarten: (row: KindergartenVO) => void;
  onSelectTeacher: (row: TeacherVO) => void;
  selectedDisplayText: string;
  hasSelection: boolean;
  onClearTarget?: () => void;
  /** 수정 폼: 교사 대상 글일 때 유치원 2단계를 미리 채움 */
  presetKindergartenForTeacherFlow?: { kindergartenId: number; name: string } | null;
  /** 감사편지 작성/수정 시 소속 유치원만 선택 가능하도록 제한 */
  lockedKindergartenId?: number | null;
  /** 감사편지 작성 화면 등 — 레이아웃·타이포를 한 단계 축소 */
  compact?: boolean;
};

export function LetterTargetPicker({
  targetType,
  onTargetTypeChange,
  onSelectKindergarten,
  onSelectTeacher,
  selectedDisplayText,
  hasSelection,
  onClearTarget,
  presetKindergartenForTeacherFlow = null,
  lockedKindergartenId = null,
  compact = false,
}: LetterTargetPickerProps) {
  const [inputQuery, setInputQuery] = useState('');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [reloadNonce, setReloadNonce] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [kindergartenRows, setKindergartenRows] = useState<KindergartenVO[]>([]);
  const [teacherRows, setTeacherRows] = useState<TeacherVO[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  /** 교사 감사: 1단계 유치원 선택 후에만 교사 목록 */
  const [pickedKgForTeacher, setPickedKgForTeacher] = useState<KindergartenVO | null>(null);
  const skipNextPresetRef = useRef(false);
  const prevTargetTypeRef = useRef<AppreciationTargetType | null>(null);

  /*
   * targetType(또는 preset)이 바뀔 때만 단계·입력을 손댐.
   * 예전에는 TEACHER+프리셋 없음마다 매번 pickedKg를 null로 두어, 유치원 클릭 직후 레이아웃에서 선택이 지워질 수 있었음.
   */
  useLayoutEffect(() => {
    const prev = prevTargetTypeRef.current;
    const targetTypeChanged = prev !== targetType;
    prevTargetTypeRef.current = targetType;

    if (targetTypeChanged) {
      setInputQuery('');
      setAppliedQuery('');
      setReloadNonce(0);
      setKindergartenRows([]);
      setTeacherRows([]);
      setError('');
    }

    if (targetType === 'KINDERGARTEN') {
      setPickedKgForTeacher(null);
    } else if (targetType === 'TEACHER') {
      if (presetKindergartenForTeacherFlow && !skipNextPresetRef.current) {
        setPickedKgForTeacher(
          minimalKindergartenVO(
            presetKindergartenForTeacherFlow.kindergartenId,
            presetKindergartenForTeacherFlow.name,
          ),
        );
      } else if (targetTypeChanged && !presetKindergartenForTeacherFlow) {
        setPickedKgForTeacher(null);
      }
    }

    skipNextPresetRef.current = false;
  }, [targetType, presetKindergartenForTeacherFlow?.kindergartenId, presetKindergartenForTeacherFlow?.name]);

  const isKgTarget = targetType === 'KINDERGARTEN';
  const teacherStepPickKg = targetType === 'TEACHER' && !pickedKgForTeacher;
  const teacherStepPickTeacher = targetType === 'TEACHER' && pickedKgForTeacher;

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const q = appliedQuery.trim();
        const lockedKg =
          lockedKindergartenId != null &&
          Number.isFinite(lockedKindergartenId) &&
          Math.trunc(lockedKindergartenId) > 0
            ? Math.trunc(lockedKindergartenId)
            : null;

        if ((isKgTarget || teacherStepPickKg) && lockedKg != null) {
          try {
            const single = await getKindergarten(lockedKg);
            if (cancelled) return;
            const rows =
              !q ||
              (single.name || '').toLowerCase().includes(q.toLowerCase()) ||
              String(single.kindergartenId).includes(q)
                ? [single]
                : [];
            setKindergartenRows(rows);
            setTeacherRows([]);
            setTotalCount(rows.length);
          } catch {
            if (cancelled) return;
            setError('소속 유치원 정보를 불러오지 못했습니다.');
            setKindergartenRows([]);
            setTeacherRows([]);
            setTotalCount(0);
          }
          return;
        }

        if (isKgTarget || teacherStepPickKg) {
          const page = await searchKindergartens({
            keyword: q,
            page: 0,
            size: LIST_PAGE_SIZE,
            sort: 'name,asc',
          });
          if (cancelled) return;
          setKindergartenRows(page.content ?? []);
          setTeacherRows([]);
          setTotalCount(page.totalElements ?? (page.content?.length ?? 0));
        } else if (teacherStepPickTeacher && pickedKgForTeacher) {
          /* 백엔드는 kindergartenId 필터 없음 → 넉넉히 받아서 프론트에서 원만 필터 */
          const kgId = pickedKgForTeacher.kindergartenId;
          const kwLower = q.toLowerCase();
          const page = await searchTeachers({
            keyword: q,
            page: 0,
            size: TEACHER_FALLBACK_SIZE,
            sort: 'name,asc',
          });
          if (cancelled) return;
          const normOpts =
            kgId > 0 ? ({ fallbackKindergartenId: kgId } as const) : undefined;
          let content = (page.content ?? []).map((row) =>
            normalizeTeacherVO(row as TeacherApiRow, normOpts),
          );
          content = content.filter((t) => t.kindergartenId === kgId);
          if (kwLower) {
            content = content.filter(
              (t) =>
                (t.name || '').toLowerCase().includes(kwLower) ||
                String(t.teacherId).includes(kwLower),
            );
          }
          setTeacherRows(content);
          setKindergartenRows([]);
          setTotalCount(content.length);
        }
      } catch (e) {
        if (cancelled) return;
        console.warn('대상 목록 조회 실패:', e);
        setError('목록을 불러오지 못했습니다. 백엔드 연결을 확인해 주세요.');
        setKindergartenRows([]);
        setTeacherRows([]);
        setTotalCount(0);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [
    targetType,
    appliedQuery,
    pickedKgForTeacher?.kindergartenId,
    reloadNonce,
    lockedKindergartenId,
  ]);

  const applySearch = () => {
    setAppliedQuery(inputQuery.trim());
  };

  const clearSearchFieldsAndReload = () => {
    setInputQuery('');
    setAppliedQuery('');
    setReloadNonce((n) => n + 1);
  };

  const resetSearchForUser = () => {
    clearSearchFieldsAndReload();
    onClearTarget?.();
  };

  const handleClickKindergartenRow = (row: KindergartenVO) => {
    const locked =
      lockedKindergartenId != null &&
      Number.isFinite(lockedKindergartenId) &&
      Math.trunc(lockedKindergartenId) > 0;
    const clickedId = Number(row.kindergartenId);
    if (locked && (!Number.isFinite(clickedId) || clickedId !== Math.trunc(lockedKindergartenId))) {
      setError('소속 유치원에 해당하는 편지만 선택할 수 있습니다.');
      return;
    }
    if (isKgTarget) {
      onSelectKindergarten(row);
      return;
    }
    skipNextPresetRef.current = false;
    setPickedKgForTeacher(row);
    clearSearchFieldsAndReload();
  };

  const rows = isKgTarget || teacherStepPickKg ? kindergartenRows : teacherRows;
  const emptyAfterLoad = !loading && !error && rows.length === 0;
  const emptyHint = appliedQuery
    ? '검색 조건에 맞는 항목이 없습니다. 검색어를 바꿔 보세요.'
    : isKgTarget || teacherStepPickKg
      ? '등록된 유치원이 없습니다.'
      : '이 유치원에 등록된 교사가 없습니다.';

  const listTitle =
    isKgTarget || teacherStepPickKg
      ? '유치원 목록'
      : `교사 목록 · ${pickedKgForTeacher?.name ?? ''}`;

  const searchPlaceholder = (() => {
    if (isKgTarget || teacherStepPickKg) return '유치원 이름으로 검색 (비우면 전체)';
    return `교사 이름으로 검색 · ${pickedKgForTeacher?.name ?? ''} 소속만`;
  })();

  const segmentBtn = compact
    ? 'rounded-md px-3 py-1.5 text-xs font-medium transition-colors border focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-1'
    : 'rounded-lg px-4 py-2 text-sm font-medium transition-colors border focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-1';

  return (
    <div
      className={
        compact
          ? 'space-y-2 rounded-lg border border-gray-200 bg-gray-50/80 p-3'
          : 'space-y-3 rounded-lg border border-gray-200 bg-gray-50/80 p-4'
      }
    >
      <p className={compact ? 'text-xs font-medium text-slate-800' : 'text-sm font-medium text-slate-800'}>
        감사 대상 선택
      </p>
      <p className={compact ? 'text-[11px] leading-snug text-slate-500' : 'text-xs text-slate-500'}>
        <strong>유치원</strong>은 목록에서 바로 고르고, <strong>유치원 교사</strong>는 먼저 유치원을 고른 뒤 그 원의 교사를 고릅니다.
      </p>

      <div className={compact ? 'flex flex-wrap gap-1.5' : 'flex flex-wrap gap-2'} role="group" aria-label="감사 대상 유형">
        <button
          type="button"
          className={`${segmentBtn} ${
            isKgTarget
              ? 'border-[#006b52] bg-[#006b52] text-white'
              : 'border-gray-300 bg-white text-slate-700 hover:bg-gray-50'
          }`}
          aria-pressed={isKgTarget}
          onClick={() => onTargetTypeChange('KINDERGARTEN')}
        >
          유치원
        </button>
        <button
          type="button"
          className={`${segmentBtn} ${
            targetType === 'TEACHER'
              ? 'border-[#006b52] bg-[#006b52] text-white'
              : 'border-gray-300 bg-white text-slate-700 hover:bg-gray-50'
          }`}
          aria-pressed={targetType === 'TEACHER'}
          onClick={() => onTargetTypeChange('TEACHER')}
        >
          유치원 교사
        </button>
      </div>

      {teacherStepPickTeacher && (
        <div
          className={
            compact
              ? 'flex flex-wrap items-center gap-1.5 rounded-md border border-emerald-100 bg-emerald-50/90 px-2 py-1.5 text-xs text-emerald-900'
              : 'flex flex-wrap items-center gap-2 rounded-md border border-emerald-100 bg-emerald-50/90 px-3 py-2 text-sm text-emerald-900'
          }
        >
          <span>
            선택한 유치원: <strong>{pickedKgForTeacher.name}</strong>
          </span>
          <button
            type="button"
            className={
              compact
                ? 'rounded border border-emerald-300 bg-white px-1.5 py-0.5 text-[11px] text-emerald-800 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-55'
                : 'rounded border border-emerald-300 bg-white px-2 py-1 text-xs text-emerald-800 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-55'
            }
            disabled={
              lockedKindergartenId != null &&
              Number.isFinite(lockedKindergartenId) &&
              Math.trunc(lockedKindergartenId) > 0
            }
            onClick={() => {
              skipNextPresetRef.current = true;
              setPickedKgForTeacher(null);
              resetSearchForUser();
            }}
          >
            다른 유치원 선택
          </button>
        </div>
      )}

      <div className={compact ? 'flex gap-1.5' : 'flex gap-2'}>
        <input
          type="text"
          value={inputQuery}
          onChange={(e) => setInputQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              applySearch();
            }
          }}
          placeholder={searchPlaceholder}
          className={
            compact
              ? 'min-w-0 flex-1 rounded-md border border-gray-300 px-2 py-1.5 text-xs'
              : 'min-w-0 flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm'
          }
        />
        <button
          type="button"
          onClick={applySearch}
          disabled={loading}
          className={
            compact
              ? 'inline-flex shrink-0 items-center gap-0.5 rounded-md bg-[#006b52] px-2.5 py-1.5 text-xs text-white hover:bg-[#005640] disabled:opacity-50'
              : 'inline-flex shrink-0 items-center gap-1 rounded-lg bg-[#006b52] px-4 py-2 text-sm text-white hover:bg-[#005640] disabled:opacity-50'
          }
        >
          <Search className={compact ? 'h-3.5 w-3.5' : 'h-4 w-4'} />
          검색
        </button>
        <button
          type="button"
          onClick={resetSearchForUser}
          className={
            compact
              ? 'shrink-0 rounded-md border border-gray-300 px-2 py-1.5 text-xs text-slate-600 hover:bg-gray-50'
              : 'shrink-0 rounded-lg border border-gray-300 px-3 py-2 text-sm text-slate-600 hover:bg-gray-50'
          }
        >
          초기화
        </button>
      </div>

      {error && (
        <p className={compact ? 'text-xs text-red-600' : 'text-sm text-red-600'}>{error}</p>
      )}

      {hasSelection && (
        <div
          className={
            compact
              ? 'rounded-md border border-emerald-200 bg-emerald-50 px-2 py-1.5 text-xs text-emerald-900'
              : 'rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900'
          }
        >
          선택됨: <span className="font-medium">{selectedDisplayText}</span>
        </div>
      )}

      <div
        className={
          compact
            ? 'flex items-center justify-between gap-1.5 text-[11px] text-slate-600'
            : 'flex items-center justify-between gap-2 text-xs text-slate-600'
        }
      >
        <span>
          {listTitle}
          {!loading && !error && (
            <span className="ml-1 font-medium text-slate-800">
              · 총 {totalCount}건
              {totalCount > LIST_PAGE_SIZE ? ` (최대 ${LIST_PAGE_SIZE}건까지 표시)` : ''}
            </span>
          )}
        </span>
        {appliedQuery ? (
          <span>
            검색어: <span className="font-medium text-slate-800">&ldquo;{appliedQuery}&rdquo;</span>
          </span>
        ) : (
          <span className="text-slate-400">전체 목록</span>
        )}
      </div>

      <div
        className={
          compact
            ? 'max-h-64 overflow-y-auto rounded-md border border-gray-200 bg-white shadow-sm'
            : 'max-h-80 overflow-y-auto rounded-md border border-gray-200 bg-white shadow-sm'
        }
      >
        {loading ? (
          <p
            className={
              compact
                ? 'p-4 text-center text-xs text-gray-500'
                : 'p-6 text-center text-sm text-gray-500'
            }
          >
            목록을 불러오는 중입니다…
          </p>
        ) : emptyAfterLoad ? (
          <p
            className={
              compact
                ? 'p-4 text-center text-xs text-gray-500'
                : 'p-6 text-center text-sm text-gray-500'
            }
          >
            {emptyHint}
          </p>
        ) : isKgTarget || teacherStepPickKg ? (
          <ul className="divide-y divide-gray-100">
            {kindergartenRows.map((row, i) => (
              <li
                key={
                  row.kindergartenId != null && Number(row.kindergartenId) > 0
                    ? row.kindergartenId
                    : `kg-${i}`
                }
              >
                <button
                  type="button"
                  onClick={() => handleClickKindergartenRow(row)}
                  disabled={
                    lockedKindergartenId != null &&
                    Number.isFinite(lockedKindergartenId) &&
                    Math.trunc(lockedKindergartenId) > 0 &&
                    Number(row.kindergartenId) !== Math.trunc(lockedKindergartenId)
                  }
                  className={
                    compact
                      ? 'flex w-full flex-col items-start gap-0.5 px-3 py-2 text-left text-xs transition-colors hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-55'
                      : 'flex w-full flex-col items-start gap-0.5 px-4 py-3 text-left text-sm transition-colors hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-55'
                  }
                >
                  <span className="font-medium text-slate-900">{row.name}</span>
                  <span className={compact ? 'text-[11px] text-gray-500' : 'text-xs text-gray-500'}>
                    유치원 ID {row.kindergartenId}
                    {row.address ? ` · ${row.address}` : ''}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <ul className="divide-y divide-gray-100">
            {teacherRows.map((row, i) => (
              <li
                key={
                  row.teacherId != null && Number(row.teacherId) > 0
                    ? `t-${row.teacherId}`
                    : `t-${i}-${row.name ?? 'row'}`
                }
              >
                <button
                  type="button"
                  onClick={() =>
                    onSelectTeacher(
                      normalizeTeacherVO(row as TeacherApiRow, {
                        fallbackKindergartenId:
                          pickedKgForTeacher && pickedKgForTeacher.kindergartenId > 0
                            ? pickedKgForTeacher.kindergartenId
                            : undefined,
                      }),
                    )
                  }
                  className={
                    compact
                      ? 'flex w-full flex-col items-start gap-0.5 px-3 py-2 text-left text-xs transition-colors hover:bg-emerald-50'
                      : 'flex w-full flex-col items-start gap-0.5 px-4 py-3 text-left text-sm transition-colors hover:bg-emerald-50'
                  }
                >
                  <span className="font-medium text-slate-900">{row.name}</span>
                  <span className={compact ? 'text-[11px] text-gray-500' : 'text-xs text-gray-500'}>
                    교사 ID {row.teacherId}
                    {row.level ? ` · 직급 ${row.level}` : ''}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
