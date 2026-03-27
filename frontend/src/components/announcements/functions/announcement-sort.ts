import type { AnnouncementListItem } from '@/services/apis/announcements.api';

const NEW_BADGE_WINDOW_MS = 7 * 24 * 60 * 60 * 1000;

export function getAnnouncementBaseDate(item: AnnouncementListItem): string | null {
  return item.publishedAt ?? item.createdAt;
}

export function isAnnouncementNew(item: AnnouncementListItem, nowMs = Date.now()): boolean {
  const baseDate = getAnnouncementBaseDate(item);
  if (!baseDate) return false;
  return nowMs - new Date(baseDate).getTime() <= NEW_BADGE_WINDOW_MS;
}

export function sortAnnouncementsByNewPriority(
  items: AnnouncementListItem[],
  nowMs = Date.now(),
): AnnouncementListItem[] {
  return [...items].sort((a, b) => {
    const aIsNew = isAnnouncementNew(a, nowMs);
    const bIsNew = isAnnouncementNew(b, nowMs);
    if (aIsNew !== bIsNew) return aIsNew ? -1 : 1;

    const aStatus = a.status ?? '';
    const bStatus = b.status ?? '';
    const statusCompare = aStatus.localeCompare(bStatus);
    if (statusCompare !== 0) return statusCompare;

    const aPinned = Boolean(a.isPinned);
    const bPinned = Boolean(b.isPinned);
    if (aPinned !== bPinned) return bPinned ? 1 : -1;

    const aBaseDate = getAnnouncementBaseDate(a);
    const bBaseDate = getAnnouncementBaseDate(b);
    const aTime = aBaseDate ? new Date(aBaseDate).getTime() : 0;
    const bTime = bBaseDate ? new Date(bBaseDate).getTime() : 0;
    if (aTime !== bTime) return bTime - aTime;

    return b.id - a.id;
  });
}
