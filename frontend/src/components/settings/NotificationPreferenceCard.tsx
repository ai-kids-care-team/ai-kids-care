'use client';

import { useState } from 'react';
import { BellRing, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/shared/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/shared/ui/card';
import { getAuthApiErrorMessage } from '@/services/apis/auth.api';
import {
  useGetMyNotificationPreferenceQuery,
  useUpdateMyNotificationPreferenceMutation,
  type NotificationPreferenceVO,
} from '@/services/apis/notificationPreferences.api';

/**
 * 본인 통지 환경설정 카드(개인설정 모달 전용) — UX-08.
 *
 * `openspec/changes/wire-notification-preferences/api-contract.md` 냉동 계약:
 * - 총 스위치(enabled)는 `RESOLVED`(비긴급) 가정통지에만 영향 — `ESCALATED`(긴급)는 항상 관통 발송.
 * - 정숙 시간대(quietHoursStart/End)는 둘 다 값이 있거나 둘 다 null 이어야 한다(단측 입력은 백엔드 400) —
 *   여기서는 단측 입력 상태를 감지해 저장 버튼을 비활성화하고 안내 문구를 보여준다(제출 전 선제 차단).
 */
export function NotificationPreferenceCard() {
  const { data, isLoading, isError } = useGetMyNotificationPreferenceQuery();
  const [updatePreference, { isLoading: isSaving }] = useUpdateMyNotificationPreferenceMutation();

  const [enabled, setEnabled] = useState(true);
  const [quietHoursStart, setQuietHoursStart] = useState('');
  const [quietHoursEnd, setQuietHoursEnd] = useState('');

  // GET 응답으로 로컬 편집 상태를 초기화한다(계약: 행 없으면 enabled=true, start/end=null).
  // 렌더 중 state 를 조정하는 공식 패턴(https://react.dev/learn/you-might-not-need-an-effect) —
  // useEffect 로 미러링하면 React 19 lint(`react-hooks/set-state-in-effect`)가 캐스케이딩 렌더로 막는다.
  const [syncedData, setSyncedData] = useState<NotificationPreferenceVO | null>(null);
  if (data && data !== syncedData) {
    setSyncedData(data);
    setEnabled(data.enabled);
    setQuietHoursStart(data.quietHoursStart ?? '');
    setQuietHoursEnd(data.quietHoursEnd ?? '');
  }

  const hasStart = quietHoursStart.trim() !== '';
  const hasEnd = quietHoursEnd.trim() !== '';
  const isSingleSided = hasStart !== hasEnd;

  const handleSave = async () => {
    if (isSingleSided) {
      toast.error('정숙 시간대는 시작·종료를 함께 입력하거나 함께 비워두어야 합니다.');
      return;
    }
    try {
      const result = await updatePreference({
        enabled,
        quietHoursStart: quietHoursStart.trim() ? quietHoursStart.trim() : null,
        quietHoursEnd: quietHoursEnd.trim() ? quietHoursEnd.trim() : null,
      }).unwrap();
      setEnabled(result.enabled);
      setQuietHoursStart(result.quietHoursStart ?? '');
      setQuietHoursEnd(result.quietHoursEnd ?? '');
      toast.success('통지 환경설정을 저장했습니다.');
    } catch (err) {
      toast.error(getAuthApiErrorMessage(err, '통지 환경설정 저장에 실패했습니다.'));
    }
  };

  return (
    <Card className="w-full">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <BellRing className="h-5 w-5 text-purple-600" />
          알림 설정
        </CardTitle>
        <CardDescription>
          긴급 알림은 이 설정과 무관하게 항상 즉시 전달됩니다. 아래 설정은 긴급하지 않은 알림에만
          적용됩니다.
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        {isLoading ? (
          <div className="flex items-center gap-2 py-3 text-sm text-gray-500">
            <Loader2 className="h-4 w-4 animate-spin" /> 불러오는 중…
          </div>
        ) : isError ? (
          <p className="py-3 text-sm text-red-600">통지 환경설정을 불러오지 못했습니다.</p>
        ) : (
          <>
            <label className="inline-flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={enabled}
                onChange={(e) => setEnabled(e.target.checked)}
              />
              알림 받기
            </label>

            <div className="space-y-2">
              <p className="text-sm font-medium text-gray-700">정숙 시간대 (선택)</p>
              <div className="flex items-center gap-2">
                <div className="space-y-1">
                  <label className="block text-xs text-gray-500" htmlFor="quiet-hours-start">
                    시작
                  </label>
                  <input
                    id="quiet-hours-start"
                    type="time"
                    value={quietHoursStart}
                    onChange={(e) => setQuietHoursStart(e.target.value)}
                    className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-500/30"
                  />
                </div>
                <span className="mt-5 text-gray-400">–</span>
                <div className="space-y-1">
                  <label className="block text-xs text-gray-500" htmlFor="quiet-hours-end">
                    종료
                  </label>
                  <input
                    id="quiet-hours-end"
                    type="time"
                    value={quietHoursEnd}
                    onChange={(e) => setQuietHoursEnd(e.target.value)}
                    className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-500/30"
                  />
                </div>
              </div>
              {isSingleSided && (
                <p className="text-xs text-red-600">
                  시작·종료를 함께 입력하거나 함께 비워두어야 저장할 수 있습니다.
                </p>
              )}
              <p className="text-xs text-gray-500">
                이 시간대에는 긴급하지 않은 알림을 받지 않습니다. 비워두면 원 전체 설정을 따릅니다.
              </p>
            </div>

            <Button onClick={handleSave} disabled={isSaving || isSingleSided} className="w-full">
              {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <BellRing className="h-4 w-4" />}
              저장
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  );
}
