import { test, expect } from '@playwright/test';
import { loginViaModal } from '../helpers/login';
import { injectDetectionEvent } from '../helpers/inject';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost';

/**
 * Flagship closed-loop Tier-2 acceptance test:
 * detection → teacher escalation → guardian in-app notification.
 *
 * Session switching: two separate browser contexts (teacher / guardian) for
 * deterministic isolation — no explicit logout step, no shared cookie jar.
 * `browser.newContext()` is used with explicit baseURL because newContext()
 * does not inherit the global `use.baseURL` from playwright.config.ts.
 *
 * Notification assertion rationale:
 *   ESCALATED always dispatches a PUSH notification to guardians (verified in
 *   GuardianNotificationService.java). The notification title is the hardcoded
 *   "안전 알림" (different from the seed SMS notification "[알림] 이상 행동 감지").
 *   Asserting getByText('안전 알림') is visible proves the escalation-triggered
 *   notification path fired; the seed notification alone cannot satisfy it.
 */
test('detection → teacher escalation → guardian in-app notification', async ({ browser, request }) => {
  // ── Step 1: Inject ─────────────────────────────────────────────────────────
  // stream_id=1 = kindergarten 1, camera 1, room 1 (demo seed).
  // A unique dedupKey per run prevents the dedup guard from skipping the event.
  const { eventType } = await injectDetectionEvent(request, { eventType: 'ASSAULT' });

  // ── Step 2: Teacher escalates ───────────────────────────────────────────────
  // Fresh context = clean cookie jar, no residual auth state.
  const teacherCtx = await browser.newContext({ baseURL: BASE_URL });
  const teacherPage = await teacherCtx.newPage();

  await loginViaModal(teacherPage, 'teacher-kg1', 'admin123');
  await teacherPage.goto('/detectionEvents');
  await expect(teacherPage.getByRole('heading', { name: '실시간 이상행동 감지' })).toBeVisible();

  // The injected event arrives via SSE push or is included in the initial REST load
  // (dashboard fetches recent events on mount). Filter by eventType; take the first
  // match = the newest card (dashboard prepends new events to the list).
  // Only events NOT in TERMINAL_STATUSES (RESOLVED, DISMISSED) show review buttons,
  // so if the event already has those statuses from a prior run, this will time out —
  // dedupKey uniqueness ensures we always get a fresh OPEN event.
  const eventItem = teacherPage.locator('ul > li').filter({ hasText: eventType }).first();
  await expect(eventItem).toBeVisible({ timeout: 15_000 });
  await eventItem.getByRole('button', { name: '에스컬레이션' }).click();

  // Optimistic update sets the status immediately; error text must stay absent.
  // Success = no rollback error message rendered on the card.
  await expect(eventItem.getByText('검토 확정에 실패했습니다')).toHaveCount(0);

  // Close teacher context. The natural latency of opening guardian context +
  // loginViaModal (modal auto-open from 401 + form fill + submit + redirect)
  // gives the @Async AFTER_COMMIT guardian notification dispatch ample time to complete.
  await teacherCtx.close();

  // ── Step 3: Guardian inbox ──────────────────────────────────────────────────
  // ESCALATED → GuardianNotificationService.deliver() → notifications row written
  // for user_id=121 (guardian-kg1, verified via seed chain: stream 1→camera 1→room 1
  // →class 1→child 1→guardian 1→user 121). listNotifications() returns it filtered
  // to the guardian's kindergarten and user_id.
  const guardianCtx = await browser.newContext({ baseURL: BASE_URL });
  const guardianPage = await guardianCtx.newPage();

  await loginViaModal(guardianPage, 'guardian-kg1', 'admin123');
  await guardianPage.goto('/notifications');
  await expect(guardianPage.getByRole('heading', { name: '알림 수신함' })).toBeVisible();

  // Wait for loading spinner to clear (loading state text disappears).
  await expect(guardianPage.getByText('알림을 불러오는 중입니다.')).toHaveCount(0, { timeout: 10_000 });

  // Strict assertion: "안전 알림" is the hardcoded title used exclusively by
  // GuardianNotificationService (not present in seed data, which has "[알림] 이상 행동 감지").
  // Its presence proves the escalation → AFTER_COMMIT dispatch → notification row path fired.
  // Use .first() because multiple runs accumulate notifications with the same title on a
  // persistent stack; we assert at least one exists, not exactly one.
  await expect(guardianPage.getByText('안전 알림').first()).toBeVisible({ timeout: 5_000 });
  // Sanity: empty-state text must also be absent.
  await expect(guardianPage.getByText('받은 알림이 없습니다.')).toHaveCount(0);

  await guardianCtx.close();
});
