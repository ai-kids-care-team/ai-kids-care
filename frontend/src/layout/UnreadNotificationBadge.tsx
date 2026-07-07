/**
 * wire-notification-read-state D5: `TopBar`의 「알림」 네비게이션 항목에 얹는 ambient 미읽음 배지.
 * 순수 표시 컴포넌트(부수효과·훅 없음) — `count` 가 0 이하면 아무것도 렌더링하지 않는다
 * (읽음 처리로 카운트가 0에 수렴하면 배지가 자연히 사라진다).
 */
export function UnreadNotificationBadge({ count }: { count: number }) {
  if (count <= 0) {
    return null;
  }

  return (
    <span
      aria-label={`읽지 않은 알림 ${count}건`}
      className="absolute -right-3 -top-2 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold leading-none text-white"
    >
      {count > 99 ? '99+' : count}
    </span>
  );
}
