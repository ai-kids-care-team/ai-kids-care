import { describe, expect, it, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { useApprovalQueue } from './useApprovalQueue';

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

interface Item {
  userId: number;
  name: string;
}

const LABELS = {
  loadErrorLog: 'load failed:',
  loadErrorToast: '목록을 불러오지 못했습니다.',
  approveSuccess: '승인했습니다.',
  approveErrorLog: 'approve failed:',
  approveErrorToast: '승인에 실패했습니다.',
  rejectSuccess: '거절했습니다.',
  rejectErrorLog: 'reject failed:',
  rejectErrorToast: '거절에 실패했습니다.',
};

/**
 * refactor-cross-cutting-debt (QLT-01/QLT-03 / D3): `useApprovalQueue` is the generic hook that
 * `useKindergartenApprovals`/`usePlatformApprovals` now wrap as pure pass-throughs.
 */
describe('useApprovalQueue', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('loads the pending queue on mount', async () => {
    const list = vi.fn().mockResolvedValue([{ userId: 1, name: 'a' }]);
    const { result } = renderHook(() =>
      useApprovalQueue<Item>({ list, approve: vi.fn(), reject: vi.fn(), labels: LABELS }),
    );

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.items).toEqual([{ userId: 1, name: 'a' }]);
    expect(result.current.error).toBe('');
  });

  it('sets error on load failure', async () => {
    const list = vi.fn().mockRejectedValue(new Error());
    const { result } = renderHook(() =>
      useApprovalQueue<Item>({ list, approve: vi.fn(), reject: vi.fn(), labels: LABELS }),
    );
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBe(LABELS.loadErrorToast);
    expect(result.current.items).toEqual([]);
  });

  it('handleApprove: removes the item from the queue on success, tracks processingUserId', async () => {
    const list = vi.fn().mockResolvedValue([
      { userId: 1, name: 'a' },
      { userId: 2, name: 'b' },
    ]);
    const approve = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() =>
      useApprovalQueue<Item>({ list, approve, reject: vi.fn(), labels: LABELS }),
    );
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.handleApprove(1);
    });

    expect(approve).toHaveBeenCalledWith(1);
    expect(result.current.items).toEqual([{ userId: 2, name: 'b' }]);
    expect(result.current.processingUserId).toBeNull();
  });

  it('handleApprove: on failure, keeps the item and re-fetches the queue', async () => {
    const list = vi.fn().mockResolvedValue([{ userId: 1, name: 'a' }]);
    const approve = vi.fn().mockRejectedValue(new Error());
    const { result } = renderHook(() =>
      useApprovalQueue<Item>({ list, approve, reject: vi.fn(), labels: LABELS }),
    );
    await waitFor(() => expect(result.current.loading).toBe(false));
    list.mockClear();

    await act(async () => {
      await result.current.handleApprove(1);
    });

    // failed approve does not optimistically remove the item, and re-queries the list
    expect(result.current.items).toEqual([{ userId: 1, name: 'a' }]);
    expect(list).toHaveBeenCalled();
    expect(result.current.processingUserId).toBeNull();
  });

  it('handleReject: removes the item from the queue on success', async () => {
    const list = vi.fn().mockResolvedValue([{ userId: 1, name: 'a' }]);
    const reject = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() =>
      useApprovalQueue<Item>({ list, approve: vi.fn(), reject, labels: LABELS }),
    );
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.handleReject(1);
    });

    expect(reject).toHaveBeenCalledWith(1);
    expect(result.current.items).toEqual([]);
  });

  it('handleReject: on failure, keeps the item and re-fetches the queue', async () => {
    const list = vi.fn().mockResolvedValue([{ userId: 1, name: 'a' }]);
    const reject = vi.fn().mockRejectedValue(new Error());
    const { result } = renderHook(() =>
      useApprovalQueue<Item>({ list, approve: vi.fn(), reject, labels: LABELS }),
    );
    await waitFor(() => expect(result.current.loading).toBe(false));
    list.mockClear();

    await act(async () => {
      await result.current.handleReject(1);
    });

    expect(result.current.items).toEqual([{ userId: 1, name: 'a' }]);
    expect(list).toHaveBeenCalled();
    expect(result.current.processingUserId).toBeNull();
  });
});
