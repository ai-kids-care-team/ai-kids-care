'use client';

import { useEffect, useRef } from 'react';
import { useSignup } from './model/useSignup';
import { GuardianForm } from './(signup)/GuardianForm';
import { KindergartenForm } from './(signup)/KindergartenForm';
import { SuperadminForm } from './(signup)/SuperadminForm';
import { PlatformItAdminForm } from './(signup)/PlatformItAdminForm';

const MEMBER_TYPES = [
  { value: 'GUARDIAN', label: '양육자', description: '자녀 정보 조회 및 알림 확인', icon: '👨‍👩‍👧' },
  { value: 'KINDERGARTEN', label: '유치원 관계자', description: '유치원 원장, 교사 및 관계자', icon: '🧑‍🏫' },
  { value: 'SUPERADMIN', label: '행정청', description: '행정청 직원 및 관계자',  icon: '🏫'  },
  { value: 'PLATFORM_IT_ADMIN', label: '플랫폼 관리자', description: '시스템 운영 및 모니터링', icon: '🛠️' },
] as const;

export function SignupForm() {
  const {
    form, onChange, memberType, handleMemberTypeChange,
    verificationCode, setVerificationCode, isCodeSent, isVerifying, isVerified, verificationMessage,
    handleSendVerificationCode, handleVerifyCode,
    childNameKeyword, setChildNameKeyword, selectedChild, isChildPopupOpen, setIsChildPopupOpen,
    childSearchKeyword, setChildSearchKeyword, childSearchResults, isChildSearching, childSearchError,
    searchChildren, openChildPopup, selectChild,
    kindergartenKeyword, setKindergartenKeyword, selectedKindergarten, isKindergartenPopupOpen, setIsKindergartenPopupOpen,
    kindergartenSearchKeyword, setKindergartenSearchKeyword, kindergartenSearchResults, isKindergartenSearching, kindergartenSearchError,
    searchKindergartens, openKindergartenPopup, selectKindergarten,
    rrnFirst6, setRrnFirst6, rrnBack7, onRrnBack7Change, gender, genderOptions,
    teacherLevelOptions,
    isPrimaryGuardian, setIsPrimaryGuardian, relationship, setRelationship, customRelationship, setCustomRelationship,
    filteredRelationshipOptions, agreeTerms, setAgreeTerms, error, fieldErrors, isSubmitting, isValid, handleSubmit
  } = useSignup();
  const startDateInputRef = useRef<HTMLInputElement | null>(null);
  const endDateInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    const keyOrder: Array<keyof typeof fieldErrors> = [
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
      'startDate',
      'endDate',
      'agreeTerms',
    ];

    const targetKey = keyOrder.find((key) => !!fieldErrors[key]);
    if (!targetKey) return;

    if (targetKey === 'startDate') {
      startDateInputRef.current?.focus();
      startDateInputRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    if (targetKey === 'endDate') {
      endDateInputRef.current?.focus();
      endDateInputRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }

    const fieldNameMap: Partial<Record<keyof typeof fieldErrors, string>> = {
      child: 'childNameKeyword',
      kindergarten: 'kindergartenKeyword',
      rrn: 'rrnFirst6',
    };

    const targetName = fieldNameMap[targetKey] ?? targetKey;
    const targetElement = document.querySelector(`[name="${targetName}"]`) as HTMLElement | null;
    targetElement?.focus();
    targetElement?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [fieldErrors]);

  return (
    <div className="w-full max-w-4xl rounded-2xl border border-slate-200 bg-white p-8 shadow-xl">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-slate-900">회원가입</h1>
        <p className="mt-2 text-slate-500 text-sm">
          회원 정보를 입력하고 회원유형을 선택해 계정을 생성하세요.
        </p>
      </div>

      <form onSubmit={handleSubmit} noValidate className="space-y-6">
        <section>
          <div className="mb-3 flex items-end justify-between">
            <h2 className="text-sm font-semibold text-slate-800">회원유형</h2>
            <p className="text-xs text-slate-500">가입할 계정 유형을 선택해주세요.</p>
          </div>
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {MEMBER_TYPES.map((type) => {
              const selected = memberType === type.value;
              return (
                <button
                  key={type.value}
                  type="button"
                  onClick={() => handleMemberTypeChange(type.value)}
                  className={`relative rounded-xl border p-4 text-left transition-all ${
                    selected
                      ? 'border-emerald-500 bg-emerald-50 shadow-[0_0_0_1px_rgba(16,185,129,0.2)]'
                      : 'border-slate-300 bg-slate-50 hover:border-slate-400 hover:bg-slate-100'
                  }`}
                  aria-pressed={selected}
                >
                  <div className="mb-2 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="text-lg" aria-hidden>
                        {type.icon}
                      </span>
                      <p className="font-semibold text-slate-900">{type.label}</p>
                    </div>
                    <span
                      className={`inline-flex h-5 min-w-5 items-center justify-center rounded-full border text-[11px] font-medium ${
                        selected
                          ? 'border-emerald-300 bg-emerald-100 text-emerald-700'
                          : 'border-slate-300 text-slate-500'
                      }`}
                    >
                      {selected ? '선택' : ''}
                    </span>
                  </div>
                  <p className="text-xs text-slate-600">{type.description}</p>
                </button>
              );
            })}
          </div>
        </section>

        {memberType === 'GUARDIAN' && (
          <GuardianForm
            form={form}
            onChange={onChange}
            childNameKeyword={childNameKeyword}
            setChildNameKeyword={setChildNameKeyword}
            selectedChild={selectedChild}
            openChildPopup={openChildPopup}
            rrnFirst6={rrnFirst6}
            setRrnFirst6={setRrnFirst6}
            rrnBack7={rrnBack7}
            onRrnBack7Change={onRrnBack7Change}
            gender={gender}
            genderOptions={genderOptions}
            relationship={relationship}
            setRelationship={setRelationship}
            filteredRelationshipOptions={filteredRelationshipOptions}
            customRelationship={customRelationship}
            setCustomRelationship={setCustomRelationship}
            isPrimaryGuardian={isPrimaryGuardian}
            setIsPrimaryGuardian={setIsPrimaryGuardian}
            fieldErrors={fieldErrors}
          />
        )}

        {memberType === 'SUPERADMIN' && (
          <SuperadminForm
            form={form}
            onChange={onChange}
            fieldErrors={fieldErrors}
          />
        )}

        {memberType === 'KINDERGARTEN' && (
          <KindergartenForm
            form={form}
            onChange={onChange}
            kindergartenKeyword={kindergartenKeyword}
            setKindergartenKeyword={setKindergartenKeyword}
            selectedKindergarten={selectedKindergarten}
            openKindergartenPopup={openKindergartenPopup}
            rrnFirst6={rrnFirst6}
            setRrnFirst6={setRrnFirst6}
            rrnBack7={rrnBack7}
            onRrnBack7Change={onRrnBack7Change}
            gender={gender}
            genderOptions={genderOptions}
            teacherLevelOptions={teacherLevelOptions}
            startDateInputRef={startDateInputRef}
            endDateInputRef={endDateInputRef}
            fieldErrors={fieldErrors}
          />
        )}

        {memberType === 'PLATFORM_IT_ADMIN' && (
          <PlatformItAdminForm
            form={form}
            onChange={onChange}
            fieldErrors={fieldErrors}
          />
        )}

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            name="agreeTerms"
            checked={agreeTerms}
            onChange={(e) => setAgreeTerms(e.target.checked)}
            className="h-4 w-4 rounded border-slate-300 bg-white"
          />
          서비스 이용약관 및 개인정보 처리방침에 동의합니다.
        </label>
        {fieldErrors.agreeTerms && <p className="text-xs text-red-500">{fieldErrors.agreeTerms}</p>}

        {error && <p className="text-sm text-red-400">{error}</p>}

        <div className="flex justify-end pt-2">
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-lg bg-emerald-600 px-4 py-2 font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isSubmitting ? '가입 처리 중...' : '회원가입'}
          </button>
        </div>
      </form>

      {isChildPopupOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
          <div className="w-[96vw] max-w-[1400px] rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-xl font-semibold text-slate-900">아이 찾기</h2>
              <button
                type="button"
                onClick={() => setIsChildPopupOpen(false)}
                className="rounded-md border border-slate-300 px-3 py-1 text-sm text-slate-700 hover:bg-slate-100"
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
                    searchChildren(childSearchKeyword);
                  }
                }}
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                placeholder="아이 이름 검색 (예: 김하린 or 이준호 or 박서윤 or 최민우 or 정지안 or 박서윤)"
              />
              <button
                type="button"
                onClick={() => searchChildren(childSearchKeyword)}
                className="min-w-16 whitespace-nowrap rounded-lg bg-emerald-600 px-4 py-2 font-medium text-white hover:bg-emerald-500"
              >
                검색
              </button>
            </div>

            {childSearchError && <p className="mb-3 text-sm text-amber-600">{childSearchError}</p>}
            {isChildSearching && <p className="mb-3 text-sm text-slate-600">검색 중...</p>}

            <div className="max-h-72 overflow-auto rounded-lg border border-slate-200">
              <table className="min-w-[1200px] whitespace-nowrap text-sm text-slate-700">
                <thead className="bg-slate-100 text-xs uppercase text-slate-600">
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
                    <tr key={child.childId} className="border-t border-slate-200">
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
                          className="whitespace-nowrap rounded-md bg-emerald-600 px-3 py-1 text-xs font-medium text-white hover:bg-emerald-500"
                        >
                          선택
                        </button>
                      </td>
                    </tr>
                  ))}
                  {childSearchResults.length === 0 && !isChildSearching && (
                    <tr>
                      <td className="px-3 py-6 text-center text-slate-500" colSpan={7}>
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

      {isKindergartenPopupOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
          <div className="w-full max-w-4xl rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-xl font-semibold text-slate-900">유치원 찾기</h2>
              <button
                type="button"
                onClick={() => setIsKindergartenPopupOpen(false)}
                className="rounded-md border border-slate-300 px-3 py-1 text-sm text-slate-700 hover:bg-slate-100"
              >
                닫기
              </button>
            </div>

            <div className="mb-4 flex flex-col gap-3 md:flex-row">
              <input
                type="text"
                value={kindergartenSearchKeyword}
                onChange={(e) => setKindergartenSearchKeyword(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    searchKindergartens(kindergartenSearchKeyword);
                  }
                }}
                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2 text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
                placeholder="유치원명 또는 사업자번호 검색 (ex) 해맑은유치원"
              />
              <button
                type="button"
                onClick={() => searchKindergartens(kindergartenSearchKeyword)}
                className="rounded-lg bg-emerald-600 px-4 py-2 font-medium text-white hover:bg-emerald-500"
              >
                검색
              </button>
            </div>

            {kindergartenSearchError && <p className="mb-3 text-sm text-amber-600">{kindergartenSearchError}</p>}
            {isKindergartenSearching && <p className="mb-3 text-sm text-slate-600">검색 중...</p>}

            <div className="max-h-72 overflow-auto rounded-lg border border-slate-200">
              <table className="min-w-full text-sm text-slate-700">
                <thead className="bg-slate-100 text-xs uppercase text-slate-600">
                  <tr>
                    <th className="px-3 py-2 text-left">ID</th>
                    <th className="px-3 py-2 text-left">유치원명</th>
                    <th className="px-3 py-2 text-left">유치원코드</th>
                    <th className="px-3 py-2 text-left">지역코드</th>
                    <th className="px-3 py-2 text-left">사업자번호</th>
                    <th className="px-3 py-2 text-left">주소</th>
                    <th className="px-3 py-2 text-left">담당자</th>
                    <th className="px-3 py-2 text-left">담당자연락처</th>
                    <th className="px-3 py-2 text-left">담당자이메일</th>
                    <th className="px-3 py-2 text-left">선택</th>
                  </tr>
                </thead>
                <tbody>
                  {kindergartenSearchResults.map((kindergarten) => (
                    <tr key={kindergarten.kindergartenId} className="border-t border-slate-200">
                      <td className="px-3 py-2">{kindergarten.kindergartenId}</td>
                      <td className="px-3 py-2">{kindergarten.name}</td>
                      <td className="px-3 py-2">{kindergarten.code ?? '-'}</td>
                      <td className="px-3 py-2">{kindergarten.regionCode ?? '-'}</td>
                      <td className="px-3 py-2">{kindergarten.businessRegistrationNo ?? '-'}</td>
                      <td className="px-3 py-2">{kindergarten.address ?? '-'}</td>
                      <td className="px-3 py-2">{kindergarten.contactName ?? '-'}</td>
                      <td className="px-3 py-2">{kindergarten.contactPhone ?? '-'}</td>
                      <td className="px-3 py-2">{kindergarten.contactEmail ?? '-'}</td>
                      <td className="px-3 py-2">
                        <button
                          type="button"
                          onClick={() => selectKindergarten(kindergarten)}
                          className="rounded-md bg-emerald-600 px-3 py-1 text-xs font-medium text-white hover:bg-emerald-500"
                        >
                          선택
                        </button>
                      </td>
                    </tr>
                  ))}
                  {kindergartenSearchResults.length === 0 && !isKindergartenSearching && (
                    <tr>
                      <td className="px-3 py-6 text-center text-slate-500" colSpan={10}>
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