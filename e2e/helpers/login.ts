import { Page, expect } from '@playwright/test';

/**
 * Opens the login modal.
 * The home page fires GET /api/v1/announcements on mount; the backend returns 401
 * for anonymous requests; apiClient's 401 interceptor dispatches window event
 * "open-login-modal", which TopBar picks up and auto-opens the modal — usually
 * within ~1 s of page load. Falls back to the TopBar button if the 401 race does
 * not fire within 5 s.
 *
 * Exported so specs can reuse it directly (e.g. the wrong-password test).
 */
export async function openLoginModal(page: Page): Promise<void> {
  const loginIdInput = page.locator('input[name="loginId"]');
  try {
    await loginIdInput.waitFor({ state: 'visible', timeout: 5_000 });
  } catch (err) {
    // Only swallow timeout errors; rethrow anything unexpected.
    if (!(err instanceof Error) || !err.message.includes('Timeout')) throw err;
    // Fallback: open the modal via the TopBar button.
    await page.getByRole('button', { name: '로그인' }).first().click();
    await loginIdInput.waitFor({ state: 'visible', timeout: 5_000 });
  }
}

/**
 * Logs in through the real UI: navigate home, open the login modal, fill the form,
 * submit, and assert success (modal closes).
 * Selectors from LoginModal.tsx:117-177.
 *
 * Implementation note — why we do NOT click the TopBar "로그인" button first:
 *   The home page fires GET /api/v1/announcements on mount. The backend returns 401
 *   for anonymous requests. apiClient's 401 interceptor dispatches window event
 *   "open-login-modal", which TopBar picks up and auto-opens the modal — BEFORE we
 *   can click the TopBar button. The backdrop (z-50) then intercepts that click.
 *   We therefore wait for the modal to appear from the 401 path (see openLoginModal).
 */
export async function loginViaModal(page: Page, loginId: string, password: string): Promise<void> {
  await page.goto('/');
  await openLoginModal(page);

  const loginIdInput = page.locator('input[name="loginId"]');
  await loginIdInput.fill(loginId);
  await page.locator('input[name="password"]').fill(password);

  // Scope to the form that contains the loginId input, avoiding any stray form or
  // TopBar "로그인" button matches. LoginModal uses a plain <div> overlay (no
  // role="dialog"), so the filter anchor is the input itself.
  await page
    .locator('form')
    .filter({ has: page.locator('input[name="loginId"]') })
    .getByRole('button', { name: /^로그인/ })
    .click();

  // Success = modal closed: the loginId input is gone.
  await expect(loginIdInput).toHaveCount(0, { timeout: 10_000 });
}
