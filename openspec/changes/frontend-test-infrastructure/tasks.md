# Tasks — frontend-test-infrastructure (C1 / QLT-02)

> 纯 frontend 域，非破坏性。develop 直接提交。

## 1. 基建
- [x] 1.1 加 devDependencies：`vitest`(4.1.9)、`@vitest/coverage-v8`(4.1.9)、`jsdom`(29.1.1)、`@testing-library/react`(16.3.2)、`@testing-library/jest-dom`(6.9.1)、`@testing-library/user-event`(14.6.1)（另加 `vite`(8.1.3)+`@vitejs/plugin-react`(6.0.3)+`@testing-library/dom`(10.4.1) 作为 vitest 4 的必需 peer；版本对齐 React 19 / TS5）
- [x] 1.2 `frontend/vitest.config.ts`：jsdom 环境、`resolve.alias`（`@` → `./src`）与 `tsconfig.json` paths 对齐、`setupFiles: ['./vitest.setup.ts']`、`include: ['src/**/*.{test,spec}.{ts,tsx}']`
- [x] 1.3 `frontend/vitest.setup.ts`：`import '@testing-library/jest-dom/vitest'`
- [x] 1.4 `package.json` scripts：`"test": "vitest"`、`"test:run": "vitest run"`（未动 lint/build）
- [x] 1.5 确认 `.gitignore` 覆盖 coverage 输出（根 `.gitignore` 已有 `.coverage/`/`coverage/`，无需改动）

## 2. 种子测试（TDD：先写断言证明能抓，再确保绿）
- [x] 2.1 `notifications.api.test.ts`：`isNotificationUnread` 对 8 个 enum 值逐值断言（护住 INT-03）；mutation probe 验证过能抓回归
- [x] 2.2 `services/apis/` 纯函数 3 处：`teachers.api.ts` `normalizeTeacherVO`（camelCase/snake_case 契约对齐）、`kindergartens.api.ts` `normalizeKindergartenVO`/`normalizeKindergartenPage`（分页 PageResponse 映射，为可测导出为 public，无行为变化）
- [x] 2.3 1 个组件渲染 smoke：`shared/ui/badge.tsx`（Badge，无副作用纯展示组件）

## 3. 门禁
- [x] 3.1 `cd frontend && npm run test:run` 全绿（4 test files / 23 tests）
- [x] 3.2 `cd frontend && npm run lint && npm run build` 不回归（0 errors/5 pre-existing warnings；static export 成功，产物 `out/` 内 grep 确认无 vitest 字符串）
- [x] 3.3 提交前已还原 `next-env.d.ts`
