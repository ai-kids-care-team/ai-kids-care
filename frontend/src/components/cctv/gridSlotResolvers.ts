/**
 * 그리드 슬롯 스트림 URL 진입점.
 *
 * 원본 CctvDashboardPage에는 데모(교통 CCTV iframe) 임시 모듈이 있었으나 두 활성화 스위치가
 * 모두 `false`였다(사용된 적 없는 죽은 코드) — cctv-dashboard-refactor-alerts (C5) 3.5에서
 * 삭제했다. 실제 카메라 재생 주소는 보안상 공개 API가 제공하지 않는다(`camera_streams`
 * 응답에 재생 URL 필드 자체가 없음 — CctvCameraDetailModal의 "재생 주소는 공개 API에서
 * 제공하지 않습니다" 참고, 결정 게이트 D-VIDEO, 본 change의 Non-goal). 따라서 두 진입점은
 * 현재 항상 `null`을 반환한다 — 실 재생 경로가 열리면 이 두 함수 본문만 교체하면 된다.
 */

export function resolvePadSlotEmbedUrl(_globalSlotIndex: number): string | null {
  return null;
}

export function resolveRealCameraTileEmbedUrl(_globalSlotIndex: number): string | null {
  return null;
}

export function padSlotTitleName(_globalSlotIndex: number, _padStreamUrl: string | null): string {
  return '빈 슬롯';
}

export function padSlotSubLine(_padStreamUrl: string | null): string {
  return '등록 카메라 없음';
}
