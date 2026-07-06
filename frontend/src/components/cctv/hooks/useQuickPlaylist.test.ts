import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useQuickPlaylist } from './useQuickPlaylist';
import type { CctvCameraVO } from '@/types/cctv.vo';

// 최소 카메라 목록(인덱스 로직만 필요) — 전체 VO 필드는 필요 없어 캐스팅.
const cameras = [
  { cameraId: 1, cameraName: 'cam-1' },
  { cameraId: 2, cameraName: 'cam-2' },
] as unknown as CctvCameraVO[];

const noopPage = () => {};

describe('useQuickPlaylist — 전체화면 순회 오버레이 닫기(회귀 방지)', () => {
  it('closePlaylist 는 인덱스를 null 로 되돌려 오버레이를 닫는다', () => {
    // 버그: 기존 닫기는 document.exitFullscreen()(브라우저 전체화면에 진입한 적이 없어
    // no-op)에만 의존해 오버레이가 닫히지 않았다. 이제 상태를 직접 되돌린다.
    const { result } = renderHook(() => useQuickPlaylist(cameras, 4, noopPage, true));

    act(() => result.current.openPlaylistAt(0));
    expect(result.current.quickPlaylistIndex).toBe(0);

    act(() => result.current.closePlaylist());
    expect(result.current.quickPlaylistIndex).toBeNull();
  });

  it('Esc 키로도 오버레이가 닫힌다', () => {
    const { result } = renderHook(() => useQuickPlaylist(cameras, 4, noopPage, true));

    act(() => result.current.openPlaylistAt(1));
    expect(result.current.quickPlaylistIndex).toBe(1);

    act(() => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    });
    expect(result.current.quickPlaylistIndex).toBeNull();
  });

  it('오버레이가 닫힌 뒤 Esc 는 아무 효과가 없다(리스너 정리 확인)', () => {
    const { result } = renderHook(() => useQuickPlaylist(cameras, 4, noopPage, true));

    // 열지 않은 상태에서 Esc → 그대로 null
    act(() => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    });
    expect(result.current.quickPlaylistIndex).toBeNull();
  });
});
