import { describe, expect, it, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

const getAiModelsMock = vi.fn();
const createAiModelMock = vi.fn();
const updateAiModelMock = vi.fn();
const deleteAiModelMock = vi.fn();

vi.mock('@/services/apis/aiModels.api', () => ({
  getAiModels: (...args: unknown[]) => getAiModelsMock(...args),
  createAiModel: (...args: unknown[]) => createAiModelMock(...args),
  updateAiModel: (...args: unknown[]) => updateAiModelMock(...args),
  deleteAiModel: (...args: unknown[]) => deleteAiModelMock(...args),
}));

const { useAiModelsManagement } = await import('./useAiModelsManagement');

/**
 * wire-orphan-management-uis (UX-01): smoke test proving the thin `useAiModelsManagement`
 * wrapper correctly forwards its aiModels.api calls and outward shape through the generic
 * `useCrudResource` — mirrors `useRoomsManagement.test.ts`'s coverage shape.
 */
describe('useAiModelsManagement (thin wrapper smoke test)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads AI models via aiModels.api and exposes the original outward shape', async () => {
    getAiModelsMock.mockResolvedValue({
      content: [{ modelId: 1, name: 'videomae-base', version: 'v1', status: 'ACTIVE' }],
      totalPages: 2,
    });

    const { result } = renderHook(() => useAiModelsManagement());

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(getAiModelsMock).toHaveBeenCalledWith({ keyword: undefined, page: 0, size: 10 });
    expect(result.current.items).toEqual([
      { modelId: 1, name: 'videomae-base', version: 'v1', status: 'ACTIVE' },
    ]);
    expect(result.current.totalPages).toBe(2);

    expect(Object.keys(result.current).sort()).toEqual(
      [
        'items',
        'loading',
        'error',
        'keyword',
        'setKeyword',
        'handleSearch',
        'page',
        'totalPages',
        'setPage',
        'submitting',
        'handleCreate',
        'handleUpdate',
        'handleDelete',
      ].sort(),
    );
  });

  it('forwards handleSearch through to a keyword-filtered getAiModels call', async () => {
    getAiModelsMock.mockResolvedValue({ content: [], totalPages: 0 });
    const { result } = renderHook(() => useAiModelsManagement());
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      result.current.setKeyword('  videomae  ');
    });
    act(() => {
      result.current.handleSearch();
    });

    await waitFor(() =>
      expect(getAiModelsMock).toHaveBeenLastCalledWith({ keyword: 'videomae', page: 0, size: 10 }),
    );
  });

  it('forwards handleCreate to aiModels.api createAiModel and reloads on success', async () => {
    getAiModelsMock.mockResolvedValue({ content: [], totalPages: 0 });
    createAiModelMock.mockResolvedValue({ modelId: 9, name: 'new-model', version: 'v1', status: 'ACTIVE' });
    const { result } = renderHook(() => useAiModelsManagement());
    await waitFor(() => expect(result.current.loading).toBe(false));
    getAiModelsMock.mockClear();

    let created: boolean | undefined;
    await act(async () => {
      created = await result.current.handleCreate({ name: 'new-model', version: 'v1', status: 'ACTIVE' });
    });

    expect(created).toBe(true);
    expect(createAiModelMock).toHaveBeenCalledWith({ name: 'new-model', version: 'v1', status: 'ACTIVE' });
    expect(getAiModelsMock).toHaveBeenCalled();
  });

  it('forwards handleUpdate to aiModels.api updateAiModel (status toggle path) and reloads on success', async () => {
    getAiModelsMock.mockResolvedValue({ content: [], totalPages: 0 });
    updateAiModelMock.mockResolvedValue({ modelId: 1, name: 'videomae-base', version: 'v1', status: 'DISABLED' });
    const { result } = renderHook(() => useAiModelsManagement());
    await waitFor(() => expect(result.current.loading).toBe(false));
    getAiModelsMock.mockClear();

    let updated: boolean | undefined;
    await act(async () => {
      updated = await result.current.handleUpdate(1, {
        name: 'videomae-base',
        version: 'v1',
        status: 'DISABLED',
      });
    });

    expect(updated).toBe(true);
    expect(updateAiModelMock).toHaveBeenCalledWith(1, {
      name: 'videomae-base',
      version: 'v1',
      status: 'DISABLED',
    });
    expect(getAiModelsMock).toHaveBeenCalled();
  });
});
