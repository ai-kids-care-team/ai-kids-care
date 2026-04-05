export function Footer() {
  return (
    <footer className="bg-[#006b52] text-gray-100">
      <div className="mx-auto w-[min(94vw,120rem)] px-[clamp(1rem,1.8vw,2rem)] py-4">
        <div className="flex items-center justify-center gap-3 text-center">
          <h3 className="text-white text-lg">AI Kids Care</h3>
          <span className="text-white/60">|</span>
          <p className="text-sm">모든 아이들의 건강하고 행복한 성장을 응원합니다.</p>
        </div>
        <div className="border-t border-white/20 mt-1.5 pt-1.5 text-sm text-center">
          <p>&copy; 2026 AI Kids Care. All rights reserved.</p>
        </div>
      </div>
    </footer>
  );
}
