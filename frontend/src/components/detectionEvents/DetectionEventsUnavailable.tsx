import Link from 'next/link';

export function DetectionEventsUnavailable() {
  return (
    <main className="min-h-screen bg-gray-50 px-4 py-10">
      <section className="mx-auto max-w-xl rounded-2xl bg-white p-8 text-center shadow">
        <h1 className="text-xl font-semibold text-slate-900">이상행동 기록 기능 준비 중</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">
          인증된 사용자와 유치원 범위를 검증하는 전용 조회 API가 마련될 때까지 감지 이벤트
          목록과 상세 정보를 제공하지 않습니다.
        </p>
        <Link
          href="/"
          className="mt-6 inline-flex rounded-lg bg-[#006b52] px-4 py-2 text-sm text-white hover:bg-[#005640]"
        >
          홈으로
        </Link>
      </section>
    </main>
  );
}
