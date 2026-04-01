'use client';

import { useEffect, useMemo, useState } from 'react';
import { BellRing, Eye, Search, CheckCircle2, XCircle, Megaphone, Info, Loader2 } from 'lucide-react';
import { getParentCommonCodeList } from '@/services/apis/commonCodes.api';
import { getEventReviews, type EventReview } from '@/services/apis/eventReviews.api';

type EventReviewFlowProps = {
  eventId: number;
};

type StatusMeta = {
  code: string;
  label: string;
};

const normalizeCodeKey = (value: string | null | undefined): string =>
  (value ?? '')
    .trim()
    .toLowerCase()
    .replace(/[\s_-]+/g, '');

function getStatusStyle(status: string) {
  const key = normalizeCodeKey(status);

  if (key === 'open') {
    return {
      icon: BellRing,
      iconClass: 'bg-rose-500 text-white',
      badgeClass: 'bg-rose-50 text-rose-600',
      cardClass: 'border-rose-100 bg-rose-50/70',
    };
  }

  if (key === 'acknowledged') {
    return {
      icon: Eye,
      iconClass: 'bg-indigo-500 text-white',
      badgeClass: 'bg-indigo-50 text-indigo-600',
      cardClass: 'border-indigo-100 bg-indigo-50/70',
    };
  }

  if (key === 'inreview') {
    return {
      icon: Search,
      iconClass: 'bg-sky-500 text-white',
      badgeClass: 'bg-sky-50 text-sky-600',
      cardClass: 'border-sky-100 bg-sky-50/70',
    };
  }

  if (key === 'resolved') {
    return {
      icon: CheckCircle2,
      iconClass: 'bg-emerald-500 text-white',
      badgeClass: 'bg-emerald-50 text-emerald-600',
      cardClass: 'border-emerald-100 bg-emerald-50/70',
    };
  }

  if (key === 'dismissed') {
    return {
      icon: XCircle,
      iconClass: 'bg-slate-500 text-white',
      badgeClass: 'bg-slate-50 text-slate-600',
      cardClass: 'border-slate-100 bg-slate-50/70',
    };
  }

  if (key === 'escalated') {
    return {
      icon: Megaphone,
      iconClass: 'bg-pink-500 text-white',
      badgeClass: 'bg-pink-50 text-pink-600',
      cardClass: 'border-pink-100 bg-pink-50/70',
    };
  }

  return {
    icon: Info,
    iconClass: 'bg-slate-400 text-white',
    badgeClass: 'bg-slate-50 text-slate-600',
    cardClass: 'border-slate-200 bg-slate-50',
  };
}

export function EventReviewFlow({ eventId }: EventReviewFlowProps) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [statusSteps, setStatusSteps] = useState<StatusMeta[]>([]);
  const [statusMetaMap, setStatusMetaMap] = useState<Record<string, StatusMeta>>({});
  const [reviews, setReviews] = useState<EventReview[]>([]);

  useEffect(() => {
    const load = async () => {
      if (!Number.isFinite(eventId) || eventId <= 0) {
        setError('유효하지 않은 이벤트 ID입니다.');
        setLoading(false);
        return;
      }

      setLoading(true);
      setError('');

      try {
        const [statusCodes, reviewList] = await Promise.all([
          getParentCommonCodeList('detection_events', 'status'),
          getEventReviews(eventId),
        ]);

        const steps: StatusMeta[] = [];
        const metaMap = statusCodes
          .slice()
          .sort((a, b) => (a.sortOrder ?? a.sort_order ?? 0) - (b.sortOrder ?? b.sort_order ?? 0))
          .reduce<Record<string, StatusMeta>>((acc, item) => {
            const normalizedCode = normalizeCodeKey(item.code);
            const label = (item.codeName ?? item.code_name)?.trim();
            if (normalizedCode && label) {
              const meta: StatusMeta = {
                code: item.code,
                label,
              };
              acc[normalizedCode] = meta;
              steps.push(meta);
            }
            return acc;
          }, {});

        setStatusMetaMap(metaMap);
        setStatusSteps(steps);
        setReviews(reviewList ?? []);
      } catch (e) {
        console.error('이벤트 리뷰 플로우 로딩 실패:', e);
        setError('처리 이력 정보를 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [eventId]);

  const items = useMemo(() => {
    const reviewByStatus = reviews.reduce<Record<string, EventReview>>((acc, review) => {
      const key = normalizeCodeKey(review.result_status);
      if (key) acc[key] = review;
      return acc;
    }, {});

    return statusSteps.map((step, index) => {
      const key = normalizeCodeKey(step.code);
      const review = reviewByStatus[key];
      return {
        id: review?.id ?? index,
        status: step.code,
        label: step.label,
        comment: review?.comment ?? null,
        hasReview: Boolean(review),
      };
    });
  }, [reviews, statusSteps]);

  if (loading) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-5">
        <div className="flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span>처리 이력 정보를 불러오는 중입니다.</span>
        </div>
      </section>
    );
  }

  if (error) {
    return (
      <section className="rounded-2xl border border-red-100 bg-red-50 p-4 text-sm text-red-600">
        {error}
      </section>
    );
  }

  if (!items.length) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
        현재까지 등록된 처리 이력이 없습니다.
      </section>
    );
  }

  return (
    <section>
      <h3 className="mb-3 text-lg font-semibold text-slate-900">처리 프로세스 플로우</h3>
      <div className="mb-3 flex items-center gap-2 text-xs text-slate-500">
        <Info className="h-3 w-3" />
        <span>AI 알림에 대해 담당자가 확인·검토·조치한 이력을 순서대로 보여줍니다.</span>
      </div>
      <div className="flex flex-wrap gap-3 rounded-2xl border border-slate-200 bg-white p-4">
        {items.map((item, index) => {
          const isLast = index === items.length - 1;
          const style = getStatusStyle(item.status);
          const Icon = style.icon;
          return (
            // eslint-disable-next-line react/no-array-index-key
            <div key={`${item.id}-${index}`} className="flex items-center gap-3">
              <div className="group relative">
                <div
                  className={`flex h-20 min-w-[140px] flex-col items-center justify-center rounded-xl border px-4 py-2 text-center text-xs text-slate-800 shadow-sm transition-colors ${
                    item.hasReview ? 'opacity-100 group-hover:border-sky-400 group-hover:bg-sky-50' : 'opacity-60'
                  } ${style.cardClass}`}
                >
                  <div className="mb-1 flex items-center gap-2">
                    <span
                      className={`flex h-6 w-6 items-center justify-center rounded-full shadow-sm ${style.iconClass}`}
                    >
                      <Icon className="h-3 w-3" />
                    </span>
                    <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${style.badgeClass}`}>
                      {item.status}
                    </span>
                  </div>
                  <div className="mt-1 text-sm font-semibold text-slate-900">{item.label}</div>
                </div>
                {item.comment && (
                  <div className="pointer-events-none absolute left-1/2 top-full z-10 mt-3 hidden w-64 -translate-x-1/2 rounded-lg border border-slate-200 bg-white p-3 text-xs text-slate-700 shadow-lg group-hover:block">
                    <div className="absolute -top-2 left-1/2 -translate-x-1/2">
                      <div className="h-0 w-0 border-x-8 border-b-8 border-x-transparent border-b-white drop-shadow-sm" />
                    </div>
                    <div className="mb-1 text-[11px] font-semibold text-slate-500">담당자 코멘트</div>
                    <p className="whitespace-pre-wrap">{item.comment}</p>
                  </div>
                )}
              </div>
              {!isLast && <div className="h-px w-6 bg-slate-300 sm:h-6 sm:w-6 sm:rotate-0" />}
            </div>
          );
        })}
      </div>
    </section>
  );
}

