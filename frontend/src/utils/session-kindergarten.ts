export function resolveViewerSessionKindergartenId(
  user: { kindergartenId?: number } | null,
): number | null {
  if (!user) return null;
  const kindergartenId = Number(user.kindergartenId);
  return Number.isFinite(kindergartenId) && kindergartenId > 0
    ? Math.trunc(kindergartenId)
    : null;
}
