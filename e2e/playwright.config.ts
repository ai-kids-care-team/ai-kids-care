import { defineConfig, devices } from '@playwright/test';

// Frontend origin (static export). The browser must run where localhost:8080
// (the backend) is also reachable — the CI runner (native) or a host-networked
// container. See plan Task B0 / Global Constraints.
const BASE_URL = process.env.BASE_URL ?? 'http://localhost';

export default defineConfig({
  testDir: './tests',
  // A hung release gate is worse than a slow one; bound every test.
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // Pre-release gate: never run flaky-parallel; one retry absorbs transient
  // SSE/render races without masking a real regression.
  fullyParallel: false,
  workers: 1,
  retries: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: BASE_URL,
    screenshot: 'on',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
