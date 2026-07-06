import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// `test.globals` is intentionally left off in vitest.config.ts (explicit describe/it/expect
// imports everywhere), which means RTL's automatic per-test cleanup (which only registers
// itself when it detects a global `afterEach`) never fires. Without this, multiple `render()`
// calls across `it` blocks in the same test file accumulate in the jsdom document instead of
// unmounting between tests, producing spurious "Found multiple elements" failures/false
// positives (surfaced by cctv-dashboard-refactor-alerts / C5's CctvTileAlertList tests, the
// first test file in this repo to render more than once per file).
afterEach(() => {
  cleanup();
});
