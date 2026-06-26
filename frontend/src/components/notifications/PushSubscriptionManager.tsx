'use client';

import { useCallback, useEffect, useState } from 'react';
import { Bell, BellOff, Loader2, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/shared/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/shared/ui/card';
import { useAppSelector } from '@/store/hook';
import { getApiErrorMessage } from '@/components/letters/api-error-message';
import {
  deletePushSubscription,
  getOwnPushSubscriptions,
  registerPushSubscription,
  updatePushSubscription,
  type PushSubscriptionVO,
} from '@/services/apis/pushSubscriptions.api';

/**
 * 본인 PUSH(Pushover) 구독 등록/관리 카드.
 *
 * 백엔드는 **Pushover** 형태다 — 브라우저 Web Push/VAPID 가 아니므로 Service Worker 가
 * 필요 없다. 사용자는 자신의 Pushover user key 를 입력해 알림 채널을 켜고, 여기서
 * 상태 전환(ACTIVE↔DISABLED)·해지(DELETE)로 관리한다.
 *
 * menu.ts(내비게이션)에는 등록하지 않는다 — 우상단 「개인설정」 모달(SettingsModal) 전용 카드다(홈 화면에는 노출하지 않는다).
 * 부수효과(목록 로드)는 effect 본문에서 직접 setState 하지 않고 async 함수로 분리한다.
 */
export function PushSubscriptionManager() {
  const isAuthenticated = useAppSelector((s) => s.user.isAuthenticated);

  const [subscriptions, setSubscriptions] = useState<PushSubscriptionVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [address, setAddress] = useState('');
  const [deviceLabel, setDeviceLabel] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const list = await getOwnPushSubscriptions();
      setSubscriptions(list);
    } catch (err) {
      toast.error(getApiErrorMessage(err, '구독 목록을 불러오지 못했습니다.'));
      setSubscriptions([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }
    void reload();
  }, [isAuthenticated, reload]);

  const handleRegister = useCallback(async () => {
    const key = address.trim();
    if (!key) {
      toast.error('Pushover user key 를 입력해 주세요.');
      return;
    }
    setSubmitting(true);
    try {
      await registerPushSubscription({
        address: key,
        ...(deviceLabel.trim() ? { deviceLabel: deviceLabel.trim() } : {}),
      });
      toast.success('PUSH 구독을 등록했습니다.');
      setAddress('');
      setDeviceLabel('');
      await reload();
    } catch (err) {
      toast.error(getApiErrorMessage(err, '구독 등록에 실패했습니다.'));
    } finally {
      setSubmitting(false);
    }
  }, [address, deviceLabel, reload]);

  const handleToggleStatus = useCallback(
    async (sub: PushSubscriptionVO) => {
      const nextStatus = sub.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
      setBusyId(sub.pushSubscriptionId);
      try {
        await updatePushSubscription(sub.pushSubscriptionId, { status: nextStatus });
        toast.success(nextStatus === 'ACTIVE' ? '알림을 켰습니다.' : '알림을 껐습니다.');
        await reload();
      } catch (err) {
        toast.error(getApiErrorMessage(err, '상태 변경에 실패했습니다.'));
      } finally {
        setBusyId(null);
      }
    },
    [reload],
  );

  const handleDelete = useCallback(
    async (sub: PushSubscriptionVO) => {
      setBusyId(sub.pushSubscriptionId);
      try {
        await deletePushSubscription(sub.pushSubscriptionId);
        toast.success('구독을 해지했습니다.');
        await reload();
      } catch (err) {
        toast.error(getApiErrorMessage(err, '구독 해지에 실패했습니다.'));
      } finally {
        setBusyId(null);
      }
    },
    [reload],
  );

  if (!isAuthenticated) {
    return null;
  }

  return (
    <Card className="w-full">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <Bell className="h-5 w-5 text-blue-600" />
          PUSH 알림 구독
        </CardTitle>
        <CardDescription>
          Pushover user key 를 등록하면 긴급 알림을 PUSH 로 받습니다.
          <a
            href="https://pushover.net/"
            target="_blank"
            rel="noreferrer"
            className="ml-1 text-blue-600 underline-offset-4 hover:underline"
          >
            user key 확인
          </a>
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        <div className="space-y-2">
          <label className="block text-sm font-medium text-gray-700" htmlFor="pushover-user-key">
            Pushover user key
          </label>
          <input
            id="pushover-user-key"
            type="text"
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            placeholder="예: u1a2b3c4d5e6f7g8h9i0j..."
            autoComplete="off"
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/30"
          />
          <label className="block text-sm font-medium text-gray-700" htmlFor="pushover-device-label">
            기기 라벨 (선택)
          </label>
          <input
            id="pushover-device-label"
            type="text"
            value={deviceLabel}
            onChange={(e) => setDeviceLabel(e.target.value)}
            placeholder="예: 내 휴대폰"
            autoComplete="off"
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/30"
          />
          <Button onClick={handleRegister} disabled={submitting} className="w-full">
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Bell className="h-4 w-4" />}
            구독 등록
          </Button>
        </div>

        <div className="border-t border-gray-200 pt-4">
          <p className="mb-2 text-sm font-medium text-gray-700">내 구독</p>
          {loading ? (
            <div className="flex items-center gap-2 py-3 text-sm text-gray-500">
              <Loader2 className="h-4 w-4 animate-spin" /> 불러오는 중…
            </div>
          ) : subscriptions.length === 0 ? (
            <p className="py-3 text-sm text-gray-500">등록된 구독이 없습니다.</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {subscriptions.map((sub) => {
                const isBusy = busyId === sub.pushSubscriptionId;
                const isActive = sub.status === 'ACTIVE';
                return (
                  <li
                    key={sub.pushSubscriptionId}
                    className="flex items-center justify-between gap-2 py-2"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm text-gray-800">
                        {sub.deviceLabel?.trim() || sub.provider}
                      </p>
                      <p className="text-xs text-gray-500">
                        {sub.provider} · {isActive ? '활성' : sub.status}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-1">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        disabled={isBusy}
                        onClick={() => handleToggleStatus(sub)}
                        title={isActive ? '알림 끄기' : '알림 켜기'}
                      >
                        {isBusy ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : isActive ? (
                          <BellOff className="h-4 w-4" />
                        ) : (
                          <Bell className="h-4 w-4" />
                        )}
                      </Button>
                      <Button
                        type="button"
                        variant="destructive"
                        size="sm"
                        disabled={isBusy}
                        onClick={() => handleDelete(sub)}
                        title="구독 해지"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
