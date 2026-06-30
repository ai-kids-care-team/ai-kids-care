import { test, expect, type Page } from '@playwright/test';
import { loginViaModal } from '../helpers/login';

/**
 * Tier-2 可发现性 / 无死胡同验收（确定性，取代已退役的 release-visual-validator 真人体验官）。
 *
 * 退役的 Tier-1「真人体验官」唯一不与正确性测试重叠的价值是两条**可确定性化**的体验断言：
 *   1. 可发现性：核心功能从落地后的主导航**直接可达**（无需先验路径地图）。
 *   2. 无死胡同：进入核心页后，仍能从页面**导航离开**（存在返回/前进出口），不会把用户困死。
 * 把它们写成确定性断言后，「找不到入口 / 走进死胡同」这类恶性体验问题在 CI 硬门禁即被拦下，
 * 不再依赖又慢又高假阴险的盲探索。
 *
 * 角色 → 主导航判别项沿用 login.spec.ts（menu.ts:38-45）：所有角色落 '/'。
 */

/** 落地后主导航是否呈现至少一个稳定锚点（证明导航已渲染、可据此发现功能）。 */
async function expectPrimaryNavVisible(page: Page, anchorLabel: string): Promise<void> {
  await expect(page.getByRole('link', { name: anchorLabel })).toBeVisible();
}

test('guardian: 核心功能从主导航直接可发现（≤1 次点击）', async ({ page }) => {
  await loginViaModal(page, 'guardian-kg1', 'admin123');

  // 可发现性：guardian 落地后，"이상 탐지" 与 "알림"(수신함) 入口应直接呈现在主导航，
  // 无需任何先验路径。getByRole('link') 命中即代表 1 次点击可达。
  await expectPrimaryNavVisible(page, '이상 탐지');
  const inboxLink = page.getByRole('link', { name: '알림' }).first();
  await expect(inboxLink).toBeVisible();
});

test('notifications: 进入核心页后仍可导航离开（无死胡同）', async ({ page }) => {
  await loginViaModal(page, 'guardian-kg1', 'admin123');

  // 进入核心页。
  await page.goto('/notifications');
  await expect(page.getByRole('heading', { name: '알림 수신함' })).toBeVisible();

  // 无死胡同：页面内仍保留通往其它核心功能的主导航出口（可前进/返回），
  // 用户不会被困在该页。"이상 탐지" 是 guardian 主导航的稳定锚点。
  await expect(page.getByRole('link', { name: '이상 탐지' })).toBeVisible();
});

test('teacher: 落地即可发现仪表盘入口（≤1 次点击）', async ({ page }) => {
  await loginViaModal(page, 'teacher-kg1', 'admin123');

  // 可发现性：teacher 的核心工作面 "대시보드" 入口落地后直接呈现。
  await expectPrimaryNavVisible(page, '대시보드');
});
