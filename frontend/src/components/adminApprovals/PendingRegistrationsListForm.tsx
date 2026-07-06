'use client';

import { UserCheck } from 'lucide-react';
import type { PendingRegistrationVO } from '@/services/apis/adminKindergarten.api';
import { roleLabels, type UserRole } from '@/types/user-role';

const LEVEL_LABELS: Record<string, string> = {
  DIRECTOR: '원장',
  VICE_DIRECTOR: '부원장',
  TEACHER: '교사',
  OTHER: '기타',
};

function roleLabel(role: string): string {
  return roleLabels[role as UserRole] ?? role;
}

function levelLabel(level: string | null): string | null {
  if (!level) return null;
  return LEVEL_LABELS[level] ?? level;
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  const hh = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd} ${hh}:${min}`;
}

type PendingRegistrationsListFormProps = {
  title: string;
  description?: string;
  items: PendingRegistrationVO[];
  loading: boolean;
  error: string;
  processingUserId: number | null;
  onApprove: (userId: number) => void;
  onReject: (userId: number) => void;
};

export function PendingRegistrationsListForm({
  title,
  description,
  items,
  loading,
  error,
  processingUserId,
  onApprove,
  onReject,
}: PendingRegistrationsListFormProps) {
  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <main className="mx-auto max-w-3xl">
        <div className="rounded-2xl bg-white p-8 shadow-lg">
          <div className="mb-8 flex items-center gap-3 border-b border-gray-200 pb-6">
            <UserCheck className="h-7 w-7 text-[#006b52]" />
            <div>
              <h2 className="text-3xl">{title}</h2>
              {description && <p className="mt-1 text-sm text-gray-500">{description}</p>}
            </div>
          </div>

          {error && (
            <p className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-600">{error}</p>
          )}

          <div className="min-h-[300px] space-y-3">
            {loading ? (
              <p className="flex min-h-[300px] items-center justify-center text-center text-gray-500">
                불러오는 중입니다.
              </p>
            ) : items.length === 0 ? (
              !error && (
                <p className="flex min-h-[300px] items-center justify-center text-center text-gray-500">
                  대기 중인 가입 신청이 없습니다.
                </p>
              )
            ) : (
              items.map((item) => {
                const processing = processingUserId === item.userId;
                const level = levelLabel(item.level);
                return (
                  <div
                    key={item.userId}
                    className="flex flex-col gap-3 rounded-lg border border-gray-200 bg-white p-5 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="rounded bg-emerald-50 px-2 py-0.5 text-xs font-semibold text-[#006b52]">
                          {roleLabel(item.role)}
                        </span>
                        {level && (
                          <span className="rounded bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
                            {level}
                          </span>
                        )}
                      </div>
                      <p className="mt-2 text-sm text-gray-500">
                        신청 ID {item.userId} · 신청일 {formatDate(item.submittedAt)}
                      </p>
                    </div>
                    <div className="flex flex-shrink-0 gap-2">
                      <button
                        type="button"
                        disabled={processing}
                        onClick={() => onReject(item.userId)}
                        className="rounded-lg border border-gray-300 px-4 py-2 text-sm text-slate-700 hover:bg-gray-50 disabled:opacity-50"
                      >
                        거절
                      </button>
                      <button
                        type="button"
                        disabled={processing}
                        onClick={() => onApprove(item.userId)}
                        className="rounded-lg bg-[#006b52] px-4 py-2 text-sm text-white hover:bg-[#005640] disabled:opacity-50"
                      >
                        {processing ? '처리 중…' : '승인'}
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
