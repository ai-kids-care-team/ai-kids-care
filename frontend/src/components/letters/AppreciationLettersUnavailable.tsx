import Link from 'next/link';

export function AppreciationLettersUnavailable() {
  return (
    <main className="min-h-screen bg-gray-50 px-4 py-10">
      <section className="mx-auto max-w-xl rounded-2xl bg-white p-8 text-center shadow">
        <h1 className="text-xl font-semibold text-slate-900">감사 편지 기능 준비 중</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">
          인증과 유치원 범위가 적용된 전용 API가 마련될 때까지 감사 편지 조회와 작성 기능을
          제공하지 않습니다.
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
