'use client';

import { Shield, User } from 'lucide-react';
import type { UserRole } from '@/types/user-role';
import { roleLabels } from '@/types/user-role';
import type { CctvCameraVO } from '@/types/cctv.vo';
import { Badge } from '@/components/shared/ui/badge';
import { Card } from '@/components/shared/ui/card';
import { displayLocationLine } from '@/lib/cctvFormat';
import type { CctvCategoryKey } from '@/lib/cctvFormat';
import { mapCameraLineStatus } from '@/components/cctv/hooks/useCctvGridData';

const ROLE_COLORS: Record<UserRole, string> = {
  SUPERADMIN: 'bg-purple-600',
  PLATFORM_IT_ADMIN: 'bg-indigo-600',
  KINDERGARTEN_ADMIN: 'bg-blue-600',
  TEACHER: 'bg-green-600',
  GUARDIAN: 'bg-orange-600',
};

const CATEGORY_TABS: readonly (readonly [CctvCategoryKey, string])[] = [
  ['all', '전체'],
  ['classroom', '교실'],
  ['corridor', '복도'],
  ['playground', '놀이터'],
  ['dining', '식당'],
  ['entrance', '현관'],
  ['hall', '강당'],
  ['security', '경비실'],
] as const;

export type CctvSidebarProps = {
  role: UserRole;
  loginIdDisplay: string;
  personNameDisplay: string;
  kindergartenAffiliationLabel: string;
  cameraStats: { total: number; online: number; offline: number };
  categoryCounts: Record<Exclude<CctvCategoryKey, 'all'>, number>;
  categoryFilter: CctvCategoryKey;
  onSelectCategory: (key: CctvCategoryKey) => void;
  filteredCameras: CctvCameraVO[];
  focusedCameraId: number | null;
  onToggleFocusCamera: (cameraId: number) => void;
};

/** 좌측 사이드바 — 피그마 Sidebar.tsx (원본 CctvDashboardPage :572-730). */
export function CctvSidebar({
  role,
  loginIdDisplay,
  personNameDisplay,
  kindergartenAffiliationLabel,
  cameraStats,
  categoryCounts,
  categoryFilter,
  onSelectCategory,
  filteredCameras,
  focusedCameraId,
  onToggleFocusCamera,
}: CctvSidebarProps) {
  const adminLike = role === 'SUPERADMIN' || role === 'PLATFORM_IT_ADMIN' || role === 'KINDERGARTEN_ADMIN';

  return (
    <div className="flex h-full w-64 shrink-0 flex-col border-r border-gray-200 bg-white">
      <div className="border-b border-gray-200 p-4">
        <div className="mb-1 flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-600">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden>
              <path
                d="M12 6C8.5 6 5.5 8 4 11c1.5 3 4.5 5 8 5s6.5-2 8-5c-1.5-3-4.5-5-8-5z"
                fill="white"
              />
              <circle cx="12" cy="11" r="2.5" fill="#7C3AED" />
            </svg>
          </div>
          <div>
            <h2 className="text-sm font-semibold text-gray-900">CCTV 모니터링</h2>
            <p className="text-xs text-gray-500">AI Kids Care</p>
          </div>
        </div>
      </div>

      <div className="border-b border-gray-200 p-4">
        <h3 className="mb-3 text-xs font-semibold uppercase text-gray-500">로그인 정보</h3>
        <Card className={`p-3 text-white ${ROLE_COLORS[role]}`}>
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white/20">
              {adminLike ? <Shield className="h-5 w-5" /> : <User className="h-5 w-5" />}
            </div>
            <div className="min-w-0 flex-1 space-y-1">
              <p suppressHydrationWarning className="truncate text-sm font-semibold">
                {loginIdDisplay}
              </p>
              <p suppressHydrationWarning className="truncate text-sm opacity-95">
                {personNameDisplay}
              </p>
              <p suppressHydrationWarning className="truncate text-sm opacity-95">
                {kindergartenAffiliationLabel}
              </p>
              <p suppressHydrationWarning className="truncate text-sm opacity-95">
                {roleLabels[role]}
              </p>
            </div>
          </div>
        </Card>
      </div>

      <div className="border-b border-gray-200 p-4">
        <h3 className="mb-3 text-xs font-semibold uppercase text-gray-500">카메라 현황</h3>
        <Card className="bg-gray-50 p-3">
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs text-gray-600">전체</span>
              <Badge variant="secondary">{cameraStats.total}대</Badge>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-gray-600">온라인</span>
              <Badge className="bg-green-500 hover:bg-green-600">{cameraStats.online}대</Badge>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-gray-600">오프라인</span>
              <Badge variant="destructive">{cameraStats.offline}대</Badge>
            </div>
          </div>
        </Card>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto p-4">
        <h3 className="mb-3 text-xs font-semibold uppercase text-gray-500">카메라 목록</h3>
        <div className="space-y-2">
          {CATEGORY_TABS.map(([key, label]) => {
            const count = key === 'all' ? cameraStats.total : categoryCounts[key];
            return (
              <button
                key={key}
                type="button"
                onClick={() => onSelectCategory(key)}
                className={`flex w-full items-center justify-between rounded-lg px-3 py-2 transition-colors ${
                  categoryFilter === key
                    ? 'border border-purple-200 bg-purple-50'
                    : 'bg-gray-50 hover:bg-gray-100'
                }`}
              >
                <span
                  className={`text-sm ${
                    categoryFilter === key ? 'font-medium text-purple-900' : 'text-gray-700'
                  }`}
                >
                  {label}
                </span>
                <Badge
                  className={categoryFilter === 'all' && key === 'all' ? 'bg-purple-600 hover:bg-purple-700' : ''}
                  variant="secondary"
                >
                  {count}
                </Badge>
              </button>
            );
          })}
          <div className="mt-3 border-t border-gray-200 pt-3">
            <div className="space-y-1.5">
              {filteredCameras.length === 0 ? (
                <p className="text-xs text-gray-400">표시할 카메라가 없습니다.</p>
              ) : (
                filteredCameras.slice(0, 14).map((c) => {
                  const st = mapCameraLineStatus(c.status);
                  const statusClass =
                    st === 'online'
                      ? 'bg-emerald-100 text-emerald-700'
                      : st === 'maintenance'
                        ? 'bg-amber-100 text-amber-700'
                        : 'bg-gray-200 text-gray-700';
                  const statusLabel = st === 'online' ? '정상' : st === 'maintenance' ? '점검' : '오프라인';
                  const isFocused = focusedCameraId === c.cameraId;
                  return (
                    <div
                      key={`cam-list-${c.cameraId}`}
                      className={`flex cursor-pointer items-center justify-between rounded px-2 py-1.5 transition-colors ${
                        isFocused ? 'bg-purple-50 ring-1 ring-purple-300' : 'bg-gray-50 hover:bg-gray-100'
                      }`}
                      onClick={() => onToggleFocusCamera(c.cameraId)}
                    >
                      <div className="min-w-0">
                        <p className="truncate text-xs font-medium text-gray-800">{c.cameraName}</p>
                        <p className="truncate text-[10px] text-gray-500">{displayLocationLine(c)}</p>
                      </div>
                      <Badge className={statusClass}>{statusLabel}</Badge>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="border-t border-gray-200 bg-gray-50 p-3">
        <p className="text-center text-xs text-gray-500">
          메뉴·접근 권한은 로그인 역할(<span className="font-medium">{roleLabels[role]}</span>)에 따릅니다.
        </p>
      </div>
    </div>
  );
}
