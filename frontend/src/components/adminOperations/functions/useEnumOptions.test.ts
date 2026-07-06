import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';

const getMock = vi.fn();

vi.mock('@/services/apis/apiClient', () => ({
  apiClient: { get: (...args: unknown[]) => getMock(...args) },
}));

const { useEnumOptions } = await import('./useEnumOptions');

const FALLBACK_LABELS: Record<string, string> = {
  MAIN: '메인',
  SUB: '보조',
};

/**
 * camera-endpoint-hygiene (C6-gap-b): `useEnumOptions`가 `useStatusOptions.ts`와 동일한
 * fetch+FALLBACK 계약을 지키는지 검증 — enum 엔드포인트 성공 시 `sortOrder`로 정렬된 코드를
 * fallback 라벨과 병합하고, 실패 시 fallback 코드/라벨을 유지해 폼이 막히지 않아야 한다.
 */
describe('useEnumOptions', () => {
  beforeEach(() => {
    getMock.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts with fallback options before the fetch resolves', () => {
    getMock.mockReturnValue(new Promise(() => {})); // never resolves during this test
    const { result } = renderHook(() => useEnumOptions('camera_stream_type', FALLBACK_LABELS));
    expect(result.current).toEqual([
      { code: 'MAIN', label: '메인' },
      { code: 'SUB', label: '보조' },
    ]);
    expect(getMock).toHaveBeenCalledWith('/enums/camera_stream_type');
  });

  it('replaces options with server codes (sorted by sortOrder) merged with fallback labels', async () => {
    getMock.mockResolvedValue({
      data: [
        { code: 'OTHER', sortOrder: 4 },
        { code: 'MAIN', sortOrder: 0 },
      ],
    });
    const { result } = renderHook(() => useEnumOptions('camera_stream_type', FALLBACK_LABELS));
    await waitFor(() =>
      expect(result.current).toEqual([
        { code: 'MAIN', label: '메인' },
        { code: 'OTHER', label: 'OTHER' }, // no fallback label for OTHER here -> code itself
      ]),
    );
  });

  it('keeps fallback options when the enum endpoint rejects', async () => {
    getMock.mockRejectedValue(new Error('network error'));
    const { result } = renderHook(() => useEnumOptions('protocol', FALLBACK_LABELS));
    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(1));
    expect(result.current).toEqual([
      { code: 'MAIN', label: '메인' },
      { code: 'SUB', label: '보조' },
    ]);
  });

  it('keeps fallback options when the server returns an empty list', async () => {
    getMock.mockResolvedValue({ data: [] });
    const { result } = renderHook(() => useEnumOptions('protocol', FALLBACK_LABELS));
    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(1));
    expect(result.current).toEqual([
      { code: 'MAIN', label: '메인' },
      { code: 'SUB', label: '보조' },
    ]);
  });
});
