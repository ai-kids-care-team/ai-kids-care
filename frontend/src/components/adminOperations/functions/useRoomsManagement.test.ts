import { describe, expect, it, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

const getRoomsMock = vi.fn();
const createRoomMock = vi.fn();
const updateRoomMock = vi.fn();
const deleteRoomMock = vi.fn();

vi.mock('@/services/apis/rooms.api', () => ({
  getRooms: (...args: unknown[]) => getRoomsMock(...args),
  createRoom: (...args: unknown[]) => createRoomMock(...args),
  updateRoom: (...args: unknown[]) => updateRoomMock(...args),
  deleteRoom: (...args: unknown[]) => deleteRoomMock(...args),
}));

const { useRoomsManagement } = await import('./useRoomsManagement');

/**
 * refactor-cross-cutting-debt (QLT-03 / 6.3): smoke test proving the now-thin
 * `useRoomsManagement` wrapper correctly forwards its rooms.api calls and outward shape
 * through the generic `useCrudResource` — i.e. the D3 refactor didn't silently drop or
 * rename any of the original hook's contract.
 */
describe('useRoomsManagement (thin wrapper smoke test)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads rooms via rooms.api and exposes the original outward shape', async () => {
    getRoomsMock.mockResolvedValue({
      content: [{ roomId: 1, name: '1반' }],
      totalPages: 2,
    });

    const { result } = renderHook(() => useRoomsManagement());

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(getRoomsMock).toHaveBeenCalledWith({ keyword: undefined, page: 0, size: 10 });
    expect(result.current.items).toEqual([{ roomId: 1, name: '1반' }]);
    expect(result.current.totalPages).toBe(2);

    // outward shape unchanged: exactly these fields, nothing added/renamed by the wrapper
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

  it('forwards handleSearch through to a keyword-filtered getRooms call', async () => {
    getRoomsMock.mockResolvedValue({ content: [], totalPages: 0 });
    const { result } = renderHook(() => useRoomsManagement());
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      result.current.setKeyword('  놀이방  ');
    });
    act(() => {
      result.current.handleSearch();
    });

    await waitFor(() =>
      expect(getRoomsMock).toHaveBeenLastCalledWith({ keyword: '놀이방', page: 0, size: 10 }),
    );
  });

  it('forwards handleCreate to rooms.api createRoom and reloads on success', async () => {
    getRoomsMock.mockResolvedValue({ content: [], totalPages: 0 });
    createRoomMock.mockResolvedValue({ roomId: 9, name: 'new' });
    const { result } = renderHook(() => useRoomsManagement());
    await waitFor(() => expect(result.current.loading).toBe(false));
    getRoomsMock.mockClear();

    let created: boolean | undefined;
    await act(async () => {
      created = await result.current.handleCreate({ name: 'new' });
    });

    expect(created).toBe(true);
    expect(createRoomMock).toHaveBeenCalledWith({ name: 'new' });
    expect(getRoomsMock).toHaveBeenCalled();
  });
});
