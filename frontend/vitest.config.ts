import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// Unit test harness for pure logic + lightweight component rendering
// (frontend-test-infrastructure / C1 / QLT-02). Does NOT touch next.config.ts,
// `lint`, or `build` — Next 16 keeps its own webpack/turbopack pipeline for the
// static export; Vitest/Vite here are test-only devDependencies.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    css: false,
  },
  resolve: {
    // Mirrors tsconfig.json `compilerOptions.paths`: { "@/*": ["./src/*"] }
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
});
