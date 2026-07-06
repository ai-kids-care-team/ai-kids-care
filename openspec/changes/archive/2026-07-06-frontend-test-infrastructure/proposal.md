## Why

前端当前**零单元测试基础设施**（`frontend/package.json` 无 vitest/jest/@testing-library，全仓 0 个 `.test`/`.spec`）。唯一的自动化网是 release 链路的 Playwright E2E（重、慢、只在发版跑）。后果：

- 最高复杂度的前端逻辑（`CctvDashboardPage.tsx` 1369 行、`services/apis/*` 的 CSRF/参数拼装、`notifications.api.ts` 的 `isNotificationUnread` enum 白名单等）**在 E2E 以下无任何测试网**——这正是 2026-07-06 分析 QLT-02（HIGH）。
- 即将到来的 CCTV god-component 重构（C5）若无单测网，拆分回归风险高。**本 change 是 C5 的前置**。

## What Changes

- **搭建前端单元测试基建**：选 **Vitest + React Testing Library + jsdom**（对 Next 16 静态导出 + React 19 + TS5 的原生 ESM/TS 支持最好，启动快，与现有 npm 工具链一致）。
  - 新增 `frontend/vitest.config.ts`（jsdom 环境、路径别名与 `tsconfig` 对齐、`setupFiles`）、`frontend/vitest.setup.ts`（RTL matchers）。
  - `package.json` 加 `"test"` / `"test:run"` 脚本与 devDependencies；不改现有 `lint`/`build`。
- **补种子测试**（证明基建可用 + 覆盖最高价值纯逻辑）：
  - `notifications.api.ts` `isNotificationUnread` 的 enum 白名单判定（QUEUED/SENDING/FAILED/DEFERRED=未读；SENT/DELIVERED/READ/CANCELED=已读）——回归护住 INT-03 修复。
  - `services/apis/` 的纯函数（参数拼装 / 分页 `PageResponse` 映射 / CSRF 头注入的可测部分），按实际可单测面选 2–3 处。
  - 1 个轻量组件渲染 smoke（验证 RTL + jsdom 通路，如某无副作用展示组件）。
- **可选 CI 接线（Non-goal 之外的建议项，本 change 只做本地脚本；是否加 CI job 留给维护者）**。

## Non-goals

- **不改 CI**（`ci.yml` 加前端 test job 属发布门变更，留给维护者单独决定）。
- **不重构** `CctvDashboardPage.tsx`（那是 C5）。
- 不追求覆盖率目标；本 change 只立基建 + 种子测试证明可用。
- 不引入 E2E/Playwright 层改动（已存在于 release 链路）。
