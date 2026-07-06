'use client';

import { useState } from 'react';
import { Plus } from 'lucide-react';
import type { CameraStreamCreatePayload, CameraStreamVO } from '@/services/apis/cctv.api';
import { useCameraStreamsManagement } from './functions/useCameraStreamsManagement';
import { useEnumOptions } from './functions/useEnumOptions';

/**
 * camera-endpoint-hygiene (C6-gap-b): `EnumMetadataService.REGISTRY`에 `camera_stream_type`/
 * `protocol`이 등록되어 `GET /api/v1/enums/{name}`로 조회 가능해졌다 — 아래 값은 더 이상
 * 1차 소스가 아니라 fetch 실패 시에만 쓰는 fallback(백엔드 `type.CameraStreamTypeEnum`/
 * `type.ProtocolEnum`과 동일해야 함; enum 값 자체를 바꿀 땐 DB·백엔드·프론트 세 곳을 함께 고친다).
 */
const STREAM_TYPE_FALLBACK_LABELS: Record<string, string> = {
  MAIN: '메인',
  SUB: '보조',
  SNAPSHOT: '스냅샷',
  RECORDING: '녹화',
  OTHER: '기타',
};
const PROTOCOL_FALLBACK_LABELS: Record<string, string> = {
  RTSP: 'RTSP',
  ONVIF: 'ONVIF',
  HTTP: 'HTTP',
  HTTPS: 'HTTPS',
};

type FormState = {
  cameraId: string;
  streamType: string;
  sourceUrl: string;
  streamUser: string;
  streamPassword: string;
  sourceProtocol: string;
  playbackUrl: string;
  playbackProtocol: string;
  fps: string;
  resolution: string;
  isPrimary: boolean;
  enabled: boolean;
};

function emptyForm(): FormState {
  return {
    cameraId: '',
    streamType: '',
    sourceUrl: '',
    streamUser: '',
    streamPassword: '',
    sourceProtocol: '',
    playbackUrl: '',
    playbackProtocol: '',
    fps: '',
    resolution: '',
    isPrimary: false,
    enabled: true,
  };
}

/** VO 에 사실 sourceUrl/streamUser 필드 자체가 없어 수정 폼은 항상 빈 값에서 시작한다. */
function toFormStateForEdit(item: CameraStreamVO): FormState {
  return {
    cameraId: String(item.cameraId),
    streamType: item.streamType ?? '',
    sourceUrl: '',
    streamUser: '',
    streamPassword: '',
    sourceProtocol: item.sourceProtocol ?? '',
    playbackUrl: '',
    playbackProtocol: item.playbackProtocol ?? '',
    fps: item.fps != null ? String(item.fps) : '',
    resolution: item.resolution ?? '',
    isPrimary: Boolean(item.isPrimary),
    enabled: item.enabled ?? true,
  };
}

/**
 * director-operations-ui (C6/UX-05): 카메라 스트림 관리 — 목록 + 생성/수정(삭제 없음, 백엔드 미지원).
 * `streamPassword` 는 제출만 하고 절대 화면에 되돌려 표시하지 않는다(VO 자체가 값을 안 돌려줌).
 */
export function CameraStreamsSection() {
  const { items, cameras, loading, error, page, totalPages, setPage, submitting, handleCreate, handleUpdate } =
    useCameraStreamsManagement();
  const streamTypeOptions = useEnumOptions('camera_stream_type', STREAM_TYPE_FALLBACK_LABELS);
  const protocolOptions = useEnumOptions('protocol', PROTOCOL_FALLBACK_LABELS);

  const [formMode, setFormMode] = useState<'closed' | 'create' | 'edit'>('closed');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [formError, setFormError] = useState('');

  const cameraName = (cameraId: number) =>
    cameras.find((c) => c.cameraId === cameraId)?.cameraName ?? `카메라 #${cameraId}`;

  const openCreate = () => {
    setForm(emptyForm());
    setEditingId(null);
    setFormError('');
    setFormMode('create');
  };

  const openEdit = (item: CameraStreamVO) => {
    setForm(toFormStateForEdit(item));
    setEditingId(item.streamId);
    setFormError('');
    setFormMode('edit');
  };

  const closeForm = () => {
    setFormMode('closed');
    setEditingId(null);
    setFormError('');
  };

  const buildPayload = (): Omit<CameraStreamCreatePayload, 'cameraId'> => {
    const payload: Omit<CameraStreamCreatePayload, 'cameraId'> = {};
    if (form.streamType) payload.streamType = form.streamType;
    if (form.sourceUrl.trim()) payload.sourceUrl = form.sourceUrl.trim();
    if (form.streamUser.trim()) payload.streamUser = form.streamUser.trim();
    if (form.streamPassword) payload.streamPassword = form.streamPassword;
    if (form.sourceProtocol) payload.sourceProtocol = form.sourceProtocol;
    if (form.playbackUrl.trim()) payload.playbackUrl = form.playbackUrl.trim();
    if (form.playbackProtocol) payload.playbackProtocol = form.playbackProtocol;
    if (form.fps.trim()) payload.fps = Number(form.fps);
    if (form.resolution.trim()) payload.resolution = form.resolution.trim();
    payload.isPrimary = form.isPrimary;
    payload.enabled = form.enabled;
    return payload;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const cameraId = Number(form.cameraId);
    if (formMode === 'create' && (!Number.isFinite(cameraId) || cameraId <= 0)) {
      setFormError('연결할 카메라를 선택해 주세요.');
      return;
    }
    setFormError('');
    const ok =
      formMode === 'create'
        ? await handleCreate({ cameraId, ...buildPayload() })
        : editingId != null
          ? await handleUpdate(editingId, buildPayload())
          : false;
    if (ok) closeForm();
  };

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-lg font-medium text-slate-800">카메라 스트림 목록</h3>
        <button
          type="button"
          onClick={openCreate}
          disabled={cameras.length === 0}
          className="flex items-center gap-1 rounded-lg bg-[#006b52] px-4 py-2 text-sm text-white hover:bg-[#005640] disabled:opacity-50"
        >
          <Plus className="h-4 w-4" />
          스트림 추가
        </button>
      </div>
      {cameras.length === 0 && !loading && (
        <p className="mb-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-700">
          등록된 카메라가 없어 스트림을 추가할 수 없습니다. 카메라 등록은 이 화면의 범위 밖입니다.
        </p>
      )}

      {formMode !== 'closed' && (
        <form onSubmit={handleSubmit} className="mb-6 space-y-4 rounded-lg border border-emerald-200 bg-emerald-50/40 p-5">
          <h4 className="text-sm font-medium text-slate-700">
            {formMode === 'create' ? '새 카메라 스트림 추가' : '카메라 스트림 정보 수정'}
          </h4>
          <div className="grid gap-4 md:grid-cols-2">
            {formMode === 'create' && (
              <div>
                <label className="mb-1 block text-sm text-slate-700">카메라</label>
                <select
                  value={form.cameraId}
                  onChange={(e) => setForm((f) => ({ ...f, cameraId: e.target.value }))}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                >
                  <option value="">선택하세요</option>
                  {cameras.map((c) => (
                    <option key={c.cameraId} value={c.cameraId}>
                      {c.cameraName}
                    </option>
                  ))}
                </select>
              </div>
            )}
            <div>
              <label className="mb-1 block text-sm text-slate-700">스트림 유형</label>
              <select
                value={form.streamType}
                onChange={(e) => setForm((f) => ({ ...f, streamType: e.target.value }))}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">선택 안 함</option>
                {streamTypeOptions.map((opt) => (
                  <option key={opt.code} value={opt.code}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-700">소스 프로토콜</label>
              <select
                value={form.sourceProtocol}
                onChange={(e) => setForm((f) => ({ ...f, sourceProtocol: e.target.value }))}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">선택 안 함</option>
                {protocolOptions.map((opt) => (
                  <option key={opt.code} value={opt.code}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-700">재생 프로토콜</label>
              <select
                value={form.playbackProtocol}
                onChange={(e) => setForm((f) => ({ ...f, playbackProtocol: e.target.value }))}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">선택 안 함</option>
                {protocolOptions.map((opt) => (
                  <option key={opt.code} value={opt.code}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-700">소스 URL</label>
              <input
                type="text"
                value={form.sourceUrl}
                onChange={(e) => setForm((f) => ({ ...f, sourceUrl: e.target.value }))}
                placeholder={formMode === 'edit' ? '변경할 경우에만 입력' : 'rtsp://...'}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-700">재생 URL</label>
              <input
                type="text"
                value={form.playbackUrl}
                onChange={(e) => setForm((f) => ({ ...f, playbackUrl: e.target.value }))}
                placeholder={formMode === 'edit' ? '변경할 경우에만 입력' : undefined}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-700">스트림 계정</label>
              <input
                type="text"
                value={form.streamUser}
                onChange={(e) => setForm((f) => ({ ...f, streamUser: e.target.value }))}
                placeholder={formMode === 'edit' ? '변경할 경우에만 입력' : undefined}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-700">스트림 비밀번호</label>
              <input
                type="password"
                value={form.streamPassword}
                onChange={(e) => setForm((f) => ({ ...f, streamPassword: e.target.value }))}
                placeholder={formMode === 'edit' ? '변경할 경우에만 입력(저장된 값은 표시되지 않음)' : undefined}
                autoComplete="new-password"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-700">FPS</label>
              <input
                type="number"
                value={form.fps}
                onChange={(e) => setForm((f) => ({ ...f, fps: e.target.value }))}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm text-slate-700">해상도</label>
              <input
                type="text"
                value={form.resolution}
                onChange={(e) => setForm((f) => ({ ...f, resolution: e.target.value }))}
                placeholder="예: 1920x1080"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div className="flex items-center gap-4">
              <label className="inline-flex items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  checked={form.isPrimary}
                  onChange={(e) => setForm((f) => ({ ...f, isPrimary: e.target.checked }))}
                />
                기본 스트림
              </label>
              <label className="inline-flex items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
                />
                사용 중
              </label>
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
              등록된 카메라 스트림이 없습니다.
            </p>
          )
        ) : (
          items.map((item) => (
            <div
              key={item.streamId}
              className="flex flex-col gap-2 rounded-lg border border-gray-200 p-4 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0 flex-1">
                <p className="font-medium text-slate-800">
                  {cameraName(item.cameraId)}{' '}
                  <span className="text-sm text-gray-500">({item.streamType ?? '유형 미설정'})</span>
                </p>
                <p className="mt-1 text-sm text-gray-500">
                  {item.sourceProtocol ?? '-'} / {item.playbackProtocol ?? '-'} · {item.resolution ?? '해상도 미설정'} ·{' '}
                  {item.fps != null ? `${item.fps}fps` : 'fps 미설정'} · {item.hasPassword ? '비밀번호 설정됨' : '비밀번호 없음'}
                  {item.isPrimary ? ' · 기본' : ''} · {item.enabled ? '사용 중' : '비활성'}
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
