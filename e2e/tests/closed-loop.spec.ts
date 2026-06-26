import { test, expect, type Page } from '@playwright/test';
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
 * FRESHNESS — why a before/after count delta (not "count > 0" / ".first() visible"):
 *   The escalation notification title ("안전 알림") and body are STATIC for every
 *   run (GuardianNotificationService.java:90-93), and the inbox API/UI expose only
 *   {notificationId, title, body, status, createdAt} (NotificationReadVO.java) — no
 *   eventId/dedupKey to anchor on. So a "안전 알림" row left by ANY prior run would
 *   keep a "≥1 visible" assertion green even if this run's review→dispatch regressed.
 *   Instead we snapshot the guardian's "안전 알림" count BEFORE escalating, then poll
 *   until it GROWS past the baseline — proving THIS run produced a fresh notification
 *   (a stale pre-existing row is already counted in `before`, so it cannot satisfy
 *   the delta; only a notification dispatched after the snapshot can).
 *
 * WHY `> before` rather than a fixed `=== before + N`:
 *   One ESCALATED review fans out to BOTH a PUSH and an SMS notification row, each
 *   carrying the same "안전 알림" title (GuardianNotificationService.java:124-139),
 *   because guardian-kg1 has a seed phone. So the real delta is +2 when SMS fires,
 *   but that couples to seed phone config. The acceptance concern is simply "a fresh
 *   guardian notification appeared", so we assert strict growth off the baseline,
 *   which is robust to the channel fan-out and to the rows arriving a moment apart.
 *
 * LATENCY — generous poll window:
 *   The notification rows are created INSIDE the @Async AFTER_COMMIT listener
 *   (GuardianNotificationService.deliver → notificationRepository.save), and the SMS
 *   leg blocks on a ~5s Solapi timeout per dispatch. Under repeated runs this async
 *   executor backlogs, delaying row creation well past a few seconds, so the poll
 *   uses a wide timeout (and the test timeout is raised to match).
 */

/** Count the guardian inbox notification cards whose title is exactly "안전 알림". */
function safetyNotifCount(page: Page): Promise<number> {
  // The title renders as <p class="...">안전 알림</p> inside each notification card;
  // exact match avoids matching any substring elsewhere on the page.
  return page.getByText('안전 알림', { exact: true }).count();
}

/** Reload the inbox and wait for the list load state to settle (spinner text gone). */
async function loadInbox(page: Page): Promise<void> {
  await page.goto('/notifications');
  await expect(page.getByRole('heading', { name: '알림 수신함' })).toBeVisible();
  await expect(page.getByText('알림을 불러오는 중입니다.')).toHaveCount(0, { timeout: 10_000 });
}

test('detection → teacher escalation → guardian in-app notification', async ({ browser, request }) => {
  // Raise the per-test budget: guardian login + inbox load + inject + teacher
  // login/escalate + a wide async-dispatch poll can exceed the 60s default.
  test.setTimeout(150_000);

  // ── Step 0: Guardian baseline ───────────────────────────────────────────────
  // Open the guardian context FIRST and snapshot how many "안전 알림" notifications
  // already exist (from seed / prior runs). This is the freshness anchor.
  const guardianCtx = await browser.newContext({ baseURL: BASE_URL });
  const guardianPage = await guardianCtx.newPage();
  await loginViaModal(guardianPage, 'guardian-kg1', 'admin123');
  await loadInbox(guardianPage);
  const before = await safetyNotifCount(guardianPage);

  // ── Step 1: Inject ──────────────────────────────────────────────────────────
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

  await teacherCtx.close();

  // ── Step 3: Guardian inbox — fresh notification delta ───────────────────────
  // ESCALATED → GuardianNotificationService.deliver() → NEW notifications row(s) for
  // user_id=121 (guardian-kg1, verified via seed chain: stream 1→camera 1→room 1
  // →class 1→child 1→guardian 1→user 121). Dispatch is @Async AFTER_COMMIT, so poll
  // (reloading the inbox each iteration) until the "안전 알림" count grows past the
  // baseline. Strict growth off `before` proves THIS run produced a fresh notification.
  await expect
    .poll(
      async () => {
        await loadInbox(guardianPage);
        return safetyNotifCount(guardianPage);
      },
      {
        message: `expected guardian "안전 알림" count to grow past baseline ${before} after escalation`,
        timeout: 60_000,
      },
    )
    .toBeGreaterThan(before);

  await guardianCtx.close();
});
