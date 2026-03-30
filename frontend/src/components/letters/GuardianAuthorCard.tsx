'use client';

import { useEffect, useState } from 'react';
import { User } from 'lucide-react';
import { useAppSelector } from '@/store/hook';
import { resolveGuardianNameFromUserKeys } from '@/services/apis/guardians.api';

type GuardianAuthorCardProps = {
  heading?: string;
  footnote?: string;
};

/**
 * 로그인 ID·회원 ID(`users.user_id`)로 Guardian API를 조회해 `guardians.name`(시드의 name)을 표시합니다.
 * 회원 ID 우선(`by-user`), 실패 시 로그인 ID(`by-login-id`) 폴백.
 */
export function GuardianAuthorCard({
  heading = '작성자',
  footnote,
}: GuardianAuthorCardProps) {
  const { user } = useAppSelector((s) => s.user);
  const [guardianName, setGuardianName] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!user) return;
    const loginId = (user.loginId || user.username || '').trim();
    const uid = Number(user.id);
    const uidOk = Number.isFinite(uid) && uid > 0;
    let cancelled = false;
    setLoading(true);
    void resolveGuardianNameFromUserKeys({
      userId: uidOk ? uid : null,
      loginId: loginId || null,
    })
      .then((name) => {
        if (!cancelled) setGuardianName(name);
      })
      .catch(() => {
        if (!cancelled) setGuardianName(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [user?.id, user?.loginId, user?.username]);

  if (!user) return null;

  const loginLabel = user.loginId || user.username || '—';

  return (
    <div className="flex gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-800">
      <User className="mt-0.5 h-5 w-5 shrink-0 text-[#006b52]" />
      <div>
        <p className="font-medium text-slate-900">{heading}</p>
        <p className="mt-1 flex flex-wrap items-baseline gap-x-2 gap-y-1">
          <span>
            <span className="text-slate-600">이름</span>{' '}
            <span className="font-medium text-slate-900">
              {loading ? '불러오는 중…' : guardianName ?? '—'}
            </span>
          </span>
          <span className="text-slate-300" aria-hidden>
            |
          </span>
          <span>
            <span className="text-slate-600">로그인 ID</span>{' '}
            <span className="font-mono font-medium text-slate-900">{loginLabel}</span>
          </span>
          <span className="text-slate-300" aria-hidden>
            |
          </span>
          <span>
            <span className="text-slate-600">회원 ID</span>{' '}
            <span className="font-mono font-medium text-slate-900">{user.id}</span>
          </span>
        </p>
        {footnote ? <p className="mt-1 text-xs text-slate-500">{footnote}</p> : null}
      </div>
    </div>
  );
}
