'use client';

import { useState } from 'react';
import { Plus, Search } from 'lucide-react';
import type { RoomVO } from '@/services/apis/rooms.api';
import { useRoomsManagement } from './functions/useRoomsManagement';
import { useStatusOptions } from './functions/useStatusOptions';

type FormState = {
  name: string;
  roomCode: string;
  locationNote: string;
  roomType: string;
  status: string;
};

function emptyForm(defaultStatus: string): FormState {
  return { name: '', roomCode: '', locationNote: '', roomType: '', status: defaultStatus };
}

function toFormState(item: RoomVO): FormState {
  return {
    name: item.name,
    roomCode: item.roomCode ?? '',
    locationNote: item.locationNote ?? '',
    roomType: item.roomType,
    status: item.status ?? '',
  };
}

/** director-operations-ui (C6/UX-05): 교실 관리 — classes 와 대칭 구조. */
export function RoomsSection() {
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
  } = useRoomsManagement();
  const statusOptions = useStatusOptions();

  const [formMode, setFormMode] = useState<'closed' | 'create' | 'edit'>('closed');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm(statusOptions[0]?.code ?? 'ACTIVE'));
  const [formError, setFormError] = useState('');

  const openCreate = () => {
    setForm(emptyForm(statusOptions[0]?.code ?? 'ACTIVE'));
    setEditingId(null);
    setFormError('');
    setFormMode('create');
  };

  const openEdit = (item: RoomVO) => {
    setForm(toFormState(item));
    setEditingId(item.roomId);
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
    if (!form.name.trim() || !form.roomType.trim()) {
      setFormError('교실명과 교실 유형은 필수입니다.');
      return;
    }
    setFormError('');
    const payload = {
      name: form.name.trim(),
      roomCode: form.roomCode.trim() || null,
      locationNote: form.locationNote.trim() || null,
      roomType: form.roomType.trim(),
      status: form.status || null,
    };
    const ok =
      formMode === 'create'
        ? await handleCreate(payload)
        : editingId != null
          ? await handleUpdate(editingId, payload)
          : false;
    if (ok) closeForm();
  };

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-lg font-medium text-slate-800">교실 목록</h3>
        <button
          type="button"
          onClick={openCreate}
          className="flex items-center gap-1 rounded-lg bg-[#006b52] px-4 py-2 text-sm text-white hover:bg-[#005640]"
        >
          <Plus className="h-4 w-4" />
          교실 추가
        </button>
      </div>

      <div className="mb-4 flex items-center gap-2">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleSearch();
          }}
          placeholder="교실명으로 검색"
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

      {formMode !== 'closed' && (
        <form onSubmit={handleSubmit} className="mb-6 space-y-4 rounded-lg border border-emerald-200 bg-emerald-50/40 p-5">
          <h4 className="text-sm font-medium text-slate-700">
            {formMode === 'create' ? '새 교실 추가' : '교실 정보 수정'}
          </h4>
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="mb-1 block text-sm text-slate-700">교실명</label>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-700">교실 유형(roomType)</label>
              <input
                type="text"
                value={form.roomType}
                onChange={(e) => setForm((f) => ({ ...f, roomType: e.target.value }))}
                placeholder="예: 놀이실"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-700">교실 코드</label>
              <input
                type="text"
                value={form.roomCode}
                onChange={(e) => setForm((f) => ({ ...f, roomCode: e.target.value }))}
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
                <option value="">선택 안 함</option>
                {statusOptions.map((opt) => (
                  <option key={opt.code} value={opt.code}>
                    {opt.label} ({opt.code})
                  </option>
                ))}
              </select>
            </div>
            <div className="md:col-span-2">
              <label className="mb-1 block text-sm text-slate-700">위치 메모</label>
              <input
                type="text"
                value={form.locationNote}
                onChange={(e) => setForm((f) => ({ ...f, locationNote: e.target.value }))}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
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
              등록된 교실이 없습니다.
            </p>
          )
        ) : (
          items.map((item) => (
            <div
              key={item.roomId}
              className="flex flex-col gap-2 rounded-lg border border-gray-200 p-4 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0 flex-1">
                <p className="font-medium text-slate-800">
                  {item.name} <span className="text-sm text-gray-500">({item.roomType})</span>
                </p>
                <p className="mt-1 text-sm text-gray-500">
                  {item.roomCode ?? '코드 없음'} · {item.locationNote ?? '위치 정보 없음'} · {item.status ?? '상태 미설정'}
                </p>
              </div>
              <div className="flex shrink-0 gap-2">
                <button
                  type="button"
                  onClick={() => openEdit(item)}
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-gray-50"
                >
                  수정
                </button>
                <button
                  type="button"
                  onClick={() => handleDelete(item.roomId)}
                  className="rounded-lg border border-red-200 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50"
                >
                  삭제
                </button>
              </div>
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
  );
}
