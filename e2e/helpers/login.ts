import { Page, expect } from '@playwright/test';

/**
 * Logs in through the real UI: open the TopBar login modal, fill the form, submit.
 * Mirrors how a real user authenticates (the frontend handles CSRF itself).
 * Asserts the modal closed (= success). Selectors from LoginModal.tsx:117-177.
 *
 * Implementation note — why we do NOT click the TopBar "로그인" button first:
 *   The home page fires GET /api/v1/announcements on mount. The backend returns 401
 *   for anonymous requests. apiClient's 401 interceptor dispatches window event
 *   "open-login-modal", which TopBar picks up and auto-opens the modal — BEFORE we
 *   can click the TopBar button. The backdrop (z-50) then intercepts that click.
 *   We therefore wait for the modal to appear from the 401 path, falling back to the
 *   TopBar button if for some reason the 401 race does not fire within 5 s.
 */
export async function loginViaModal(page: Page, loginId: string, password: string): Promise<void> {
  await page.goto('/');

  const loginIdInput = page.locator('input[name="loginId"]');
  try {
    // Modal is usually auto-opened by the 401 interceptor within ~1 s of page load.
    await loginIdInput.waitFor({ state: 'visible', timeout: 5_000 });
  } catch {
    // Fallback: if the modal did not auto-open, open it via the TopBar button.
    await page.getByRole('button', { name: '로그인' }).first().click();
    await loginIdInput.waitFor({ state: 'visible', timeout: 5_000 });
  }

  await loginIdInput.fill(loginId);
  await page.locator('input[name="password"]').fill(password);
  // The modal's submit button. LoginModal uses a plain <div> overlay (no role="dialog"),
  // so we scope to the <form> element to avoid matching the TopBar "로그인" button.
  await page.locator('form').getByRole('button', { name: /^로그인/ }).click();
  // Success = modal closed: the loginId input is gone.
  await expect(loginIdInput).toHaveCount(0, { timeout: 10_000 });
}
