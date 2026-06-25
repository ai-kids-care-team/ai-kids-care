import { apiClient } from './apiClient';

/**
 * Minimal child view returned by the relationship-scoped endpoint.
 * Mirrors backend {@code GuardianChildVO(childId, name, status)}.
 * Only children with an ACTIVE guardian↔child relationship are returned;
 * the scope is enforced in SQL — no extra client-side filtering needed.
 */
export type GuardianChildVO = {
  childId: number;
  name: string;
  status: string;
};

/**
 * List all children related to the authenticated guardian (or teacher-assigned children).
 *
 * Backend: {@code GET /api/v1/children}
 * Authorization: CHILD_READ (@PreAuthorize on ChildrenService); guardian sees only
 * children with an ACTIVE relationship; teachers see their assigned class children.
 *
 * @param keyword - Optional name filter applied client-side on the returned list.
 *                  The backend endpoint does not yet accept a keyword param, so we
 *                  filter locally (list is small — bounded by guardian's own children).
 */
export async function searchChildrenByName(
  keyword: string,
  _page = 0,
  _size = 20,
): Promise<GuardianChildVO[]> {
  const response = await apiClient.get<GuardianChildVO[]>('/children');
  const all = response.data ?? [];
  if (!keyword || !keyword.trim()) return all;
  const kw = keyword.trim().toLowerCase();
  return all.filter((c) => c.name.toLowerCase().includes(kw));
}

/**
 * Fetch a single child by ID.
 *
 * Backend: {@code GET /api/v1/children/{id}}
 * Returns 404 (wrapped as error) if the caller has no active relationship to the child.
 */
export async function getChild(id: number): Promise<GuardianChildVO> {
  const response = await apiClient.get<GuardianChildVO>(`/children/${id}`);
  return response.data;
}
