import { describe, expect, it, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { useCrudResource } from './useCrudResource';

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

interface Item {
  id: number;
  name: string;
}

const LABELS = {
  loadErrorLog: 'load failed:',
  loadErrorToast: '목록을 불러오지 못했습니다.',
  createSuccess: '추가했습니다.',
  createErrorToast: '추가에 실패했습니다.',
  updateSuccess: '수정했습니다.',
  updateErrorToast: '수정에 실패했습니다.',
  deleteConfirm: '정말 삭제하시겠습니까?',
  deleteSuccess: '삭제했습니다.',
  deleteErrorToast: '삭제에 실패했습니다.',
};

function makePage(items: Item[], totalPages = 1) {
  return { content: items, totalPages };
}

/**
 * refactor-cross-cutting-debt (QLT-01/QLT-03 / D3): `useCrudResource` is the generic hook that
 * `useClassesManagement`/`useRoomsManagement`/`useCameraStreamsManagement` now wrap thinly.
 * These tests exercise the generic directly so regressions surface here rather than only via
 * the (now much thinner) wrapper hooks.
 */
describe('useCrudResource', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('loads items and totalPages on mount', async () => {
    const list = vi.fn().mockResolvedValue(makePage([{ id: 1, name: 'a' }], 3));
    const { result } = renderHook(() =>
      useCrudResource<Item>({ list, labels: LABELS }),
    );

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.items).toEqual([{ id: 1, name: 'a' }]);
    expect(result.current.totalPages).toBe(3);
    expect(result.current.error).toBe('');
    expect(list).toHaveBeenCalledWith({ keyword: undefined, page: 0, size: 10 });
  });

  it('sets error on load failure and stops loading', async () => {
    const list = vi.fn().mockRejectedValue(new Error());
    const { result } = renderHook(() => useCrudResource<Item>({ list, labels: LABELS }));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBe(LABELS.loadErrorToast);
    expect(result.current.items).toEqual([]);
  });

  it('handleSearch trims the keyword, applies it, and resets to page 0', async () => {
    const list = vi.fn().mockResolvedValue(makePage([]));
    const { result } = renderHook(() => useCrudResource<Item>({ list, labels: LABELS }));
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      result.current.setPage(2);
    });
    await waitFor(() => expect(result.current.page).toBe(2));

    act(() => {
      result.current.setKeyword('  hello  ');
    });
    act(() => {
      result.current.handleSearch();
    });

    expect(result.current.appliedKeyword).toBe('hello');
    expect(result.current.page).toBe(0);
    await waitFor(() =>
      expect(list).toHaveBeenLastCalledWith({ keyword: 'hello', page: 0, size: 10 }),
    );
  });

  it('does not apply keyword filtering when hasKeyword is false', async () => {
    const list = vi.fn().mockResolvedValue(makePage([]));
    const { result } = renderHook(() =>
      useCrudResource<Item>({ list, labels: LABELS, hasKeyword: false }),
    );
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      result.current.setKeyword('ignored');
    });
    act(() => {
      result.current.handleSearch();
    });
    await waitFor(() =>
      expect(list).toHaveBeenLastCalledWith({ keyword: undefined, page: 0, size: 10 }),
    );
  });

  it('handleCreate: reloads and toggles submitting on success', async () => {
    const list = vi.fn().mockResolvedValue(makePage([{ id: 1, name: 'a' }]));
    const create = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() => useCrudResource<Item>({ list, create, labels: LABELS }));
    await waitFor(() => expect(result.current.loading).toBe(false));
    list.mockClear();

    let createResult: boolean | undefined;
    await act(async () => {
      createResult = await result.current.handleCreate({ name: 'b' });
    });

    expect(createResult).toBe(true);
    expect(create).toHaveBeenCalledWith({ name: 'b' });
    expect(list).toHaveBeenCalled();
    expect(result.current.submitting).toBe(false);
  });

  it('handleCreate: returns false and does not throw on failure', async () => {
    const list = vi.fn().mockResolvedValue(makePage([]));
    const create = vi.fn().mockRejectedValue(new Error());
    const { result } = renderHook(() => useCrudResource<Item>({ list, create, labels: LABELS }));
    await waitFor(() => expect(result.current.loading).toBe(false));

    let createResult: boolean | undefined;
    await act(async () => {
      createResult = await result.current.handleCreate({ name: 'x' });
    });

    expect(createResult).toBe(false);
    expect(result.current.submitting).toBe(false);
  });

  it('handleUpdate: reloads on success, returns false on failure', async () => {
    const list = vi.fn().mockResolvedValue(makePage([]));
    const update = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() => useCrudResource<Item>({ list, update, labels: LABELS }));
    await waitFor(() => expect(result.current.loading).toBe(false));
    list.mockClear();

    let updateResult: boolean | undefined;
    await act(async () => {
      updateResult = await result.current.handleUpdate(1, { name: 'y' });
    });
    expect(updateResult).toBe(true);
    expect(update).toHaveBeenCalledWith(1, { name: 'y' });
    expect(list).toHaveBeenCalled();

    update.mockRejectedValueOnce(new Error());
    await act(async () => {
      updateResult = await result.current.handleUpdate(1, { name: 'z' });
    });
    expect(updateResult).toBe(false);
  });

  it('handleDelete: skips the request when the confirm gate is declined', async () => {
    const list = vi.fn().mockResolvedValue(makePage([]));
    const remove = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { result } = renderHook(() => useCrudResource<Item>({ list, remove, labels: LABELS }));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.handleDelete(1);
    });
    expect(remove).not.toHaveBeenCalled();
  });

  it('handleDelete: deletes and reloads on confirm; swallows errors on failure', async () => {
    const list = vi.fn().mockResolvedValue(makePage([]));
    const remove = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { result } = renderHook(() => useCrudResource<Item>({ list, remove, labels: LABELS }));
    await waitFor(() => expect(result.current.loading).toBe(false));
    list.mockClear();

    await act(async () => {
      await result.current.handleDelete(1);
    });
    expect(remove).toHaveBeenCalledWith(1);
    expect(list).toHaveBeenCalled();

    remove.mockRejectedValueOnce(new Error());
    await act(async () => {
      await expect(result.current.handleDelete(1)).resolves.toBeUndefined();
    });
  });

  it('handleDelete is a no-op when hasDelete is false', async () => {
    const list = vi.fn().mockResolvedValue(makePage([]));
    const remove = vi.fn();
    const { result } = renderHook(() =>
      useCrudResource<Item>({ list, remove, labels: LABELS, hasDelete: false }),
    );
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.handleDelete(1);
    });
    expect(remove).not.toHaveBeenCalled();
  });

  it('carries the optional extra payload from the loader through to the result', async () => {
    const list = vi.fn().mockResolvedValue({ content: [], totalPages: 1, extra: ['sideData'] });
    const { result } = renderHook(() => useCrudResource<Item, unknown, unknown, string[]>({ list, labels: LABELS }));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.extra).toEqual(['sideData']);
  });
});
