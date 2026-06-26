import { test, expect } from '@playwright/test';
import { loginViaModal } from '../helpers/login';

// Role → nav discriminator (menu.ts:38-45). All roles land on '/'.
const ROLES = [
  { loginId: 'teacher-kg1',  sees: '대시보드',  hidesDashboard: false },
  { loginId: 'director-kg1', sees: '대시보드',  hidesDashboard: false },
  { loginId: 'guardian-kg1', sees: '이상 탐지', hidesDashboard: true },
  { loginId: 'admin',        sees: '이상 탐지', hidesDashboard: true },
];

for (const role of ROLES) {
  test(`${role.loginId} can log in with admin123 and reach their home`, async ({ page }) => {
    await loginViaModal(page, role.loginId, 'admin123');
    await expect(page.getByRole('link', { name: role.sees })).toBeVisible();
    if (role.hidesDashboard) {
      await expect(page.getByRole('link', { name: '대시보드' })).toHaveCount(0);
    }
  });
}

test('wrong password is rejected (sanity: the gate can actually fail)', async ({ page }) => {
  await page.goto('/');
  // The home page fires GET /announcements → 401 → apiClient auto-opens the modal.
  // Wait for modal to appear; fall back to TopBar button click if it doesn't fire.
  const loginIdInput = page.locator('input[name="loginId"]');
  try {
    await loginIdInput.waitFor({ state: 'visible', timeout: 5_000 });
  } catch {
    await page.getByRole('button', { name: '로그인' }).first().click();
    await loginIdInput.waitFor({ state: 'visible', timeout: 5_000 });
  }
  await loginIdInput.fill('teacher-kg1');
  await page.locator('input[name="password"]').fill('definitely-wrong');
  // Scope to form to avoid matching the TopBar "로그인" button.
  await page.locator('form').getByRole('button', { name: /^로그인/ }).click();
  // Modal stays open (login failed): the input is still present.
  await expect(loginIdInput).toBeVisible();
});
