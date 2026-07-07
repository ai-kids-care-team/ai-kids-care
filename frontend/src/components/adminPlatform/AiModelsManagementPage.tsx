'use client';

import { useState } from 'react';
import { Cpu, Plus, Search } from 'lucide-react';
import { useAppSelector } from '@/store/hook';
import { openLoginModal } from '@/utils/auth-modal';
import type { AiModelVO } from '@/services/apis/aiModels.api';
import { useAiModelsManagement } from './functions/useAiModelsManagement';
import { useStatusOptions } from '@/components/adminOperations/functions/useStatusOptions';

type FormState = {
  name: string;
  version: string;
  status: string;
};

function emptyForm(defaultStatus: string): FormState {
  return { name: '', version: '', status: defaultStatus };
}

function toFormState(item: AiModelVO): FormState {
  return { name: item.name, version: item.version, status: item.status ?? '' };
}

/**
 * wire-orphan-management-uis (UX-01): 플랫폼 관리자 전용 「AI 모델 관리」 화면.
 *
 * `AiModelController`/`AiModelService` 권한: `PLATFORM_METADATA_READ` 는 PLATFORM_IT_ADMIN +
 * SUPERADMIN 둘 다 허용하지만 `PLATFORM_METADATA_WRITE`(생성/수정/삭제) 는 PLATFORM_IT_ADMIN
 * 전용이다 — 프론트 게이트도 동일하게: SUPERADMIN 은 조회만, 쓰기 컨트롤은 숨긴다. 백엔드
 * `@PreAuthorize` 가 최종 판정을 하므로 여기서는 진입점/UI 노출만 거칠게 맞춘다.
 *
 * 범위는 메타데이터 대장(name/version/status)뿐 — 학습 트리거/가중치 업로드/스트림별 모델
 * 바인딩 활성화는 비범위(design D4, 기존 `/api/v1/ai_models` 엔드포인트만 소비).
 *
 * D4 (「정지」의미): `deleteAiModel`은 하드 삭제이고 `detection_sessions.model_id`가
 * `nullable=false` FK 로 과거 세션이 모델을 참조할 수 있어 삭제가 DB 제약 위반으로 실패할
 * 수 있다. 그래서 「정지」버튼은 `updateAiModel`로 `status`를 ACTIVE ↔ DISABLED 로 토글하는
 * 소프트 경로를 쓰고, 하드 삭제는 별도의 "삭제" 버튼(강한 확인 문구 포함)으로 남겨둔다.
 */
export function AiModelsManagementPage() {
  const { user, isAuthenticated } = useAppSelector((state) => state.user);
  const {
    items,
    loading,
    error,
    keyword,
    setKeyword,
    handleSearch,
    page,
    totalPages,
    setPage,
    submitting,
    handleCreate,
    handleUpdate,
    handleDelete,
  } = useAiModelsManagement();
  const statusOptions = useStatusOptions();

  const [formMode, setFormMode] = useState<'closed' | 'create' | 'edit'>('closed');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm(statusOptions[0]?.code ?? 'ACTIVE'));
  const [formError, setFormError] = useState('');

  if (!isAuthenticated || !user) {
    return (
      <div className="min-h-screen bg-gray-50 p-6">
        <main className="mx-auto max-w-3xl">
          <div className="rounded-2xl bg-white p-8 text-center shadow-lg">
            <p className="mb-4 text-sm text-slate-600">로그인이 필요합니다.</p>
            <button
              type="button"
              onClick={() => openLoginModal()}
              className="rounded-lg bg-[#006b52] px-5 py-2 text-white hover:bg-[#005640]"
            >
              로그인
            </button>
          </div>
        </main>
      </div>
    );
  }

  const canWrite = user.role === 'PLATFORM_IT_ADMIN';
  const canRead = canWrite || user.role === 'SUPERADMIN';

  if (!canRead) {
    return (
      <div className="min-h-screen bg-gray-50 p-6">
        <main className="mx-auto max-w-3xl">
          <div className="rounded-2xl bg-white p-8 text-center shadow-lg">
            <p className="text-sm text-slate-600">AI 모델 관리는 시스템 관리자 계정만 이용할 수 있습니다.</p>
          </div>
        </main>
      </div>
    );
  }

  const openCreate = () => {
    setForm(emptyForm(statusOptions[0]?.code ?? 'ACTIVE'));
    setEditingId(null);
    setFormError('');
    setFormMode('create');
  };

  const openEdit = (item: AiModelVO) => {
    setForm(toFormState(item));
    setEditingId(item.modelId);
    setFormError('');
    setFormMode('edit');
  };

  const closeForm = () => {
    setFormMode('closed');
    setEditingId(null);
    setFormError('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim() || !form.version.trim()) {
      setFormError('모델명과 버전은 필수입니다.');
      return;
    }
    setFormError('');
    const payload = {
      name: form.name.trim(),
      version: form.version.trim(),
      status: form.status || (statusOptions[0]?.code ?? 'ACTIVE'),
    };
    const ok =
      formMode === 'create'
        ? await handleCreate(payload)
        : editingId != null
          ? await handleUpdate(editingId, payload)
          : false;
    if (ok) closeForm();
  };

  const toggleActive = async (item: AiModelVO) => {
    const next = item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    await handleUpdate(item.modelId, { name: item.name, version: item.version, status: next });
  };

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-5xl">
        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-6 flex items-center gap-3 border-b border-gray-200 pb-6">
            <Cpu className="h-7 w-7 text-[#006b52]" />
            <div>
              <h2 className="text-3xl">AI 모델 관리</h2>
              <p className="mt-1 text-sm text-gray-500">
                플랫폼에 등록된 AI 모델(name/version/status) 대장을 조회
                {canWrite ? '·등록·수정·정지합니다.' : '합니다.'}
              </p>
            </div>
          </div>

          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-lg font-medium text-slate-800">모델 목록</h3>
            {canWrite && (
              <button
                type="button"
                onClick={openCreate}
                className="flex items-center gap-1 rounded-lg bg-[#006b52] px-4 py-2 text-sm text-white hover:bg-[#005640]"
              >
                <Plus className="h-4 w-4" />
                모델 등록
              </button>
            )}
          </div>

          <div className="mb-4 flex items-center gap-2">
            <input
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSearch();
              }}
              placeholder="모델명으로 검색"
              className="w-full rounded-lg border border-gray-300 px-4 py-2 text-sm text-slate-900 focus:border-transparent focus:ring-2 focus:ring-emerald-500"
            />
            <button
              type="button"
              onClick={handleSearch}
              className="inline-flex shrink-0 items-center gap-1 rounded-lg bg-[#006b52] px-4 py-2 text-sm text-white hover:bg-[#005640]"
            >
              <Search className="h-4 w-4" />
              검색
            </button>
          </div>

          {canWrite && formMode !== 'closed' && (
            <form onSubmit={handleSubmit} className="mb-6 space-y-4 rounded-lg border border-emerald-200 bg-emerald-50/40 p-5">
              <h4 className="text-sm font-medium text-slate-700">
                {formMode === 'create' ? '새 AI 모델 등록' : 'AI 모델 정보 수정'}
              </h4>
              <div className="grid gap-4 md:grid-cols-2">
                <div>
                  <label className="mb-1 block text-sm text-slate-700">모델명</label>
                  <input
                    type="text"
                    value={form.name}
                    onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-sm text-slate-700">버전</label>
                  <input
                    type="text"
                    value={form.version}
                    onChange={(e) => setForm((f) => ({ ...f, version: e.target.value }))}
                    placeholder="예: v1.2.0"
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-sm text-slate-700">상태</label>
                  <select
                    value={form.status}
                    onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                  >
                    {statusOptions.map((opt) => (
                      <option key={opt.code} value={opt.code}>
                        {opt.label} ({opt.code})
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              {formError && <p className="text-sm text-red-600">{formError}</p>}
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={closeForm}
                  className="rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-100"
                >
                  취소
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-lg bg-[#006b52] px-4 py-2 text-sm text-white hover:bg-[#005640] disabled:opacity-50"
                >
                  {submitting ? '저장 중...' : '저장'}
                </button>
              </div>
            </form>
          )}

          {error && <p className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-600">{error}</p>}

          <div className="min-h-[200px] space-y-2">
            {loading ? (
              <p className="flex min-h-[200px] items-center justify-center text-center text-gray-500">불러오는 중입니다.</p>
            ) : items.length === 0 ? (
              !error && (
                <p className="flex min-h-[200px] items-center justify-center text-center text-gray-500">
                  등록된 AI 모델이 없습니다.
                </p>
              )
            ) : (
              items.map((item) => (
                <div
                  key={item.modelId}
                  className="flex flex-col gap-2 rounded-lg border border-gray-200 p-4 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-slate-800">
                      {item.name} <span className="text-sm text-gray-500">({item.version})</span>
                    </p>
                    <p className="mt-1 text-sm text-gray-500">
                      {statusOptions.find((o) => o.code === item.status)?.label ?? item.status ?? '상태 미설정'}
                    </p>
                  </div>
                  {canWrite && (
                    <div className="flex shrink-0 gap-2">
                      <button
                        type="button"
                        onClick={() => toggleActive(item)}
                        disabled={submitting}
                        className="rounded-lg border border-amber-300 px-3 py-1.5 text-sm text-amber-700 hover:bg-amber-50 disabled:opacity-50"
                      >
                        {item.status === 'ACTIVE' ? '정지' : '활성화'}
                      </button>
                      <button
                        type="button"
                        onClick={() => openEdit(item)}
                        className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-gray-50"
                      >
                        수정
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(item.modelId)}
                        className="rounded-lg border border-red-200 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50"
                      >
                        삭제
                      </button>
                    </div>
                  )}
                </div>
              ))
            )}
          </div>

          {!loading && totalPages > 1 && (
            <div className="mt-6 flex items-center justify-center gap-3 border-t border-gray-100 pt-4">
              <button
                type="button"
                disabled={page <= 0}
                onClick={() => setPage(page - 1)}
                className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                이전
              </button>
              <span className="text-sm text-gray-600">
                {page + 1} / {totalPages}
              </span>
              <button
                type="button"
                disabled={page >= totalPages - 1}
                onClick={() => setPage(page + 1)}
                className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                다음
              </button>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
