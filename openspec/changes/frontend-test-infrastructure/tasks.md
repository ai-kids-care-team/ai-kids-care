# Tasks — frontend-test-infrastructure (C1 / QLT-02)

> 纯 frontend 域，非破坏性。develop 直接提交。

## 1. 基建
- [ ] 1.1 加 devDependencies：`vitest`、`@vitest/coverage-v8`（可选）、`jsdom`、`@testing-library/react`、`@testing-library/jest-dom`、`@testing-library/user-event`（版本对齐 React 19 / TS5）
- [ ] 1.2 `frontend/vitest.config.ts`：jsdom 环境、`resolve.alias` 与 `tsconfig.json` paths 对齐、`setupFiles: ['./vitest.setup.ts']`、`include: ['src/**/*.{test,spec}.{ts,tsx}']`
- [ ] 1.3 `frontend/vitest.setup.ts`：`import '@testing-library/jest-dom/vitest'`
- [ ] 1.4 `package.json` scripts：`"test": "vitest"`、`"test:run": "vitest run"`（不动 lint/build）
- [ ] 1.5 确认 `.gitignore` 覆盖 coverage 输出（若启用）

## 2. 种子测试（TDD：先写断言证明能抓，再确保绿）
- [ ] 2.1 `notifications.api.test.ts`：`isNotificationUnread` 对 8 个 enum 值逐值断言（护住 INT-03）
- [ ] 2.2 `services/apis/` 纯函数 2–3 处（分页映射 / 参数拼装 / 可测的 CSRF 注入面）
- [ ] 2.3 1 个组件渲染 smoke（RTL + jsdom 通路验证）

## 3. 门禁
- [ ] 3.1 `cd frontend && npm run test:run` 全绿
- [ ] 3.2 `cd frontend && npm run lint && npm run build` 不回归（新增 devDeps 不破坏静态导出）
- [ ] 3.3 提交前还原 `next-env.d.ts`
