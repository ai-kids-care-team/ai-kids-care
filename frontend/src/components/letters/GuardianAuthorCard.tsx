'use client';

import { useEffect, useState } from 'react';
import { User } from 'lucide-react';
import { useAppSelector } from '@/store/hook';
import { resolveGuardianNameFromUserKeys } from '@/services/apis/guardians.api';
import { getLoginIdByUserId } from '@/services/apis/usersPublic.api';

type GuardianAuthorCardProps = {
  heading?: string;
  footnote?: string;
};

/**
 * 이름: Guardian API(회원 ID·로그인 ID 키).
 * 로그인 ID: Redux에 없으면 `getLoginIdByUserId`로 보강.
 * 회원 ID: Redux `user.id`(숫자 user_id 권장).
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
    const loginFromStore = (user.loginId || user.username || '').trim();
    const idStr = String(user.id ?? '').trim();
    const uid = Number(idStr);
    /** 숫자만인 `user.id`는 회원 PK로만 본다. 그 외(로그인 문자열 등)는 표시용 로그인 ID로 쓴다 */
    const idLooksLikeNumericUserId = idStr !== '' && /^\d+$/.test(idStr);
    const uidOk = idLooksLikeNumericUserId && Number.isFinite(uid) && uid > 0;
    const seedLogin =
      loginFromStore || (!idLooksLikeNumericUserId && idStr ? idStr : '');
    let cancelled = false;
    setLoading(true);

    void (async () => {
      try {
        let displayLogin = seedLogin;
        if (!displayLogin && uidOk) {
          displayLogin = (await getLoginIdByUserId(uid))?.trim() ?? '';
        }

        const name = await resolveGuardianNameFromUserKeys({
          userId: uidOk ? uid : null,
          loginId: displayLogin || seedLogin || null,
        });
        if (!cancelled) setGuardianName(name);
      } catch {
        if (!cancelled) {
          setGuardianName(null);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [user?.id, user?.loginId, user?.username]);

  if (!user) return null;

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
        </p>
        {footnote ? <p className="mt-1 text-xs text-slate-500">{footnote}</p> : null}
      </div>
    </div>
  );
}
