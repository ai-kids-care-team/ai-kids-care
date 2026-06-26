import { test, expect } from '@playwright/test';
import { loginViaModal, openLoginModal } from '../helpers/login';

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
  // openLoginModal waits for auto-open and falls back to the TopBar button.
  await openLoginModal(page);
  const loginIdInput = page.locator('input[name="loginId"]');
  await loginIdInput.fill('teacher-kg1');
  await page.locator('input[name="password"]').fill('definitely-wrong');
  // Scope to the form containing the loginId input to avoid matching the TopBar button.
  await page
    .locator('form')
    .filter({ has: page.locator('input[name="loginId"]') })
    .getByRole('button', { name: /^로그인/ })
    .click();
  // Modal stays open (login failed): the input is still present.
  await expect(loginIdInput).toBeVisible();
});
