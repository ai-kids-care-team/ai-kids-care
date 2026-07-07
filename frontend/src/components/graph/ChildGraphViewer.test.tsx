import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const searchChildrenByNameMock = vi.fn();
vi.mock('@/services/apis/children.api', () => ({
  searchChildrenByName: (...args: unknown[]) => searchChildrenByNameMock(...args),
}));

const searchTeachersMock = vi.fn();
vi.mock('@/services/apis/teachers.api', () => ({
  searchTeachers: (...args: unknown[]) => searchTeachersMock(...args),
}));

const useGetChildGraphQueryMock = vi.fn();
const useGetTeacherGraphQueryMock = vi.fn();
vi.mock('@/services/apis/graph.api', () => ({
  useGetChildGraphQuery: (...args: unknown[]) => useGetChildGraphQueryMock(...args),
  useGetTeacherGraphQuery: (...args: unknown[]) => useGetTeacherGraphQueryMock(...args),
}));

const { ChildGraphViewer } = await import('./ChildGraphViewer');

/**
 * wire-orphan-management-uis (UX-02): before this change, 'child' mode's *only* entry point was
 * typing a raw numeric childId — an orphan UX for anyone who didn't already know the PK. This
 * suite proves the real child selector (backed by the previously-unused `GET /api/v1/children`
 * via `searchChildrenByName`) carries the selected `childId` into `useGetChildGraphQuery`, and
 * that the direct-ID fallback path still works when the list is empty (KINDERGARTEN_ADMIN case —
 * `ChildrenService.listRelatedChildren` returns `[]` for that role).
 *
 * 'teacher' mode carried the same residual orphan UX (raw teacherId typed by hand only). It now
 * reuses the already-existing `searchTeachers` (`GET /api/v1/teachers`) to offer a name-based
 * roster select mirroring the child selector, keeping manual-ID entry as a fallback for callers
 * who can't see a roster (e.g. the endpoint returning an empty page for their role).
 */
describe('ChildGraphViewer child selector wiring', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useGetChildGraphQueryMock.mockReturnValue({ data: undefined, isLoading: false, isError: false });
    useGetTeacherGraphQueryMock.mockReturnValue({ data: undefined, isLoading: false, isError: false });
    searchTeachersMock.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 100,
      number: 0,
      first: true,
      last: true,
    });
  });

  it('selecting a child from the roster select carries its childId into the graph query', async () => {
    searchChildrenByNameMock.mockResolvedValue([
      { childId: 42, name: '홍길동', status: 'ACTIVE' },
      { childId: 7, name: '김유아', status: 'ACTIVE' },
    ]);
    const user = userEvent.setup();

    render(<ChildGraphViewer />);

    await waitFor(() => expect(screen.getByRole('combobox')).toBeInTheDocument());
    const select = screen.getByRole('combobox');
    await user.selectOptions(select, '42');

    await waitFor(() =>
      expect(useGetChildGraphQueryMock).toHaveBeenLastCalledWith(42, expect.objectContaining({ skip: false })),
    );
  });

  it('falls back to manual childId entry when the roster list is empty', async () => {
    searchChildrenByNameMock.mockResolvedValue([]);
    const user = userEvent.setup();

    render(<ChildGraphViewer />);

    await waitFor(() =>
      expect(screen.getByText('선택 가능한 원아가 없습니다. 아래에서 원아 ID를 직접 입력해 조회하세요.')).toBeInTheDocument(),
    );

    const input = screen.getByPlaceholderText('원아 ID 직접 입력');
    await user.type(input, '99');
    await user.click(screen.getByRole('button', { name: '조회' }));

    await waitFor(() =>
      expect(useGetChildGraphQueryMock).toHaveBeenLastCalledWith(99, expect.objectContaining({ skip: false })),
    );
  });

  it('switching to teacher mode swaps the child roster select for the teacher roster select', async () => {
    searchChildrenByNameMock.mockResolvedValue([{ childId: 1, name: 'A', status: 'ACTIVE' }]);
    searchTeachersMock.mockResolvedValue({
      content: [{ teacherId: 5, kindergartenId: 1, userId: 10, name: '박선생', gender: null, level: null, startDate: null, endDate: null, status: 'ACTIVE', createdAt: null, updatedAt: null }],
      totalElements: 1,
      totalPages: 1,
      size: 100,
      number: 0,
      first: true,
      last: true,
    });
    const user = userEvent.setup();

    render(<ChildGraphViewer />);
    await waitFor(() => expect(searchChildrenByNameMock).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchTeachersMock).toHaveBeenCalledTimes(1));

    await user.click(screen.getByRole('button', { name: '교사 기준' }));

    // Still exactly one combobox visible (child's select unmounts, teacher's mounts) — proves
    // the teacher roster selector replaces the child one rather than stacking alongside it.
    await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(1));
    expect(screen.getByText('박선생 (#5)')).toBeInTheDocument();
  });

  it('selecting a teacher from the roster select carries its teacherId into the graph query', async () => {
    searchTeachersMock.mockResolvedValue({
      content: [
        { teacherId: 5, kindergartenId: 1, userId: 10, name: '박선생', gender: null, level: null, startDate: null, endDate: null, status: 'ACTIVE', createdAt: null, updatedAt: null },
        { teacherId: 8, kindergartenId: 1, userId: 11, name: '이선생', gender: null, level: null, startDate: null, endDate: null, status: 'ACTIVE', createdAt: null, updatedAt: null },
      ],
      totalElements: 2,
      totalPages: 1,
      size: 100,
      number: 0,
      first: true,
      last: true,
    });
    const user = userEvent.setup();

    render(<ChildGraphViewer />);
    await user.click(screen.getByRole('button', { name: '교사 기준' }));

    await waitFor(() => expect(screen.getByRole('combobox')).toBeInTheDocument());
    const select = screen.getByRole('combobox');
    await user.selectOptions(select, '8');

    await waitFor(() =>
      expect(useGetTeacherGraphQueryMock).toHaveBeenLastCalledWith(8, expect.objectContaining({ skip: false })),
    );
  });

  it('falls back to manual teacherId entry when the teacher roster list is empty', async () => {
    searchTeachersMock.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 100,
      number: 0,
      first: true,
      last: true,
    });
    const user = userEvent.setup();

    render(<ChildGraphViewer />);
    await user.click(screen.getByRole('button', { name: '교사 기준' }));

    await waitFor(() =>
      expect(screen.getByText('선택 가능한 교사가 없습니다. 아래에서 교사 ID를 직접 입력해 조회하세요.')).toBeInTheDocument(),
    );

    const input = screen.getByPlaceholderText('교사 ID');
    await user.type(input, '5');
    await user.click(screen.getByRole('button', { name: '조회' }));

    await waitFor(() =>
      expect(useGetTeacherGraphQueryMock).toHaveBeenLastCalledWith(5, expect.objectContaining({ skip: false })),
    );
  });
});
