## Why

登录后，主页（`HomePage`）右栏底部会突然出现一块「PUSH 알림 구독 / Pushover user key」输入卡片。它由 `PushSubscriptionManager` 自带 `isAuthenticated` 门控（登出时 `return null`），所以只在登录后冒出来，给人"主页被塞了个不该在这儿的设置框"的观感。Pushover user key 属于个人偏好设置，应收纳进右上角的个人设置入口，而非占据主页首屏。

## What Changes

- 从 `HomePage` 移除 `PushSubscriptionManager` 的无条件挂载（止血：主页底部不再出现 Pushover 输入框）。
- 在顶栏（`TopBar`）右上角把当前的静态用户名展示改造为**下拉入口**，下拉含「개인설정」与「로그아웃」两项（登出从独立按钮收进下拉；游客态保持原"로그인"按钮不变）。
- 新增**个人设置弹窗**（`SettingsModal`，沿用现有 `LoginModal` 手写 Modal 模式），「개인설정」点击后打开，弹窗内承载 `PushSubscriptionManager`。
- 自助 PUSH 订阅管理 UI 的**露出位置**由"主页卡片"改为"个人设置弹窗"——成为一条可追溯的能力需求。
- 纯前端改动：复用既有 `pushSubscriptions.api.ts`，**无后端、无 DB schema、无 API 契约变更**。

## Non-goals

- 不实现独立 `/settings` 路由页（本次用弹窗承载；将来如需更多设置项再升级为页面）。
- 不改动 `PushSubscriptionManager` 的内部逻辑、状态机或 API 调用（仅迁移挂载位置 + 更新陈旧注释）。
- 不触及后端订阅管理 API、通知投递链路、`push_subscriptions` schema。
- 不新增「개인설정」下除 PUSH 订阅以外的设置项（密码修改等留待后续）。

## Capabilities

### New Capabilities
<!-- 无新增能力。 -->

### Modified Capabilities
- `notifications`: 新增一条关于「自助 PUSH(Pushover) 订阅管理 UI 露出位置」的需求——该 UI SHALL 经由登录用户右上角的个人设置入口访问，且 SHALL NOT 默认呈现在主页首屏。仅 UI 露出契约变化；后端订阅管理 API、投递生命周期、`push_subscriptions` schema 均不变。

## Impact

- 受影响前端代码：
  - `frontend/src/components/home/HomePage.tsx`（移除挂载与 import）
  - `frontend/src/layout/TopBar.tsx`（用户名下拉入口 + 渲染设置弹窗）
  - `frontend/src/components/settings/SettingsModal.tsx`（**新增**）
  - `frontend/src/components/notifications/PushSubscriptionManager.tsx`（仅更新陈旧 docstring，逻辑不动）
- 不受影响：后端、AI、DB、`pushSubscriptions.api.ts`、通知投递链路。
- 验证依赖：本机无 node，前端 `lint + build` 经 docker `node:20` DooD 跑（提交前还原 `next-env.d.ts`）。
