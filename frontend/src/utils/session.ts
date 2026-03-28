import type { User } from '@/store/slices/userSlice';

/** Redux 복구 전 첫 렌더에서도 동일한 세션 정보를 쓰기 위한 스냅샷 (메뉴·뱃지 깜박임 방지). */
export function readStoredUserSnapshot(): User | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = localStorage.getItem('user');
    if (!raw) return null;
    return JSON.parse(raw) as User;
  } catch {
    return null;
  }
}
