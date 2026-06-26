# Design — relocate-push-subscription-to-settings

## Context

`PushSubscriptionManager` 当前被 `HomePage.tsx:142-144` 无条件挂在右栏底部（公告卡片下方）。组件自带 `isAuthenticated` 门控（`PushSubscriptionManager.tsx:120-122` 在未登录时 `return null`），所以仅在登录后露出，造成"登录后主页底部突然冒出 Pushover 输入框"的观感。目标：把它收纳进右上角个人设置入口。

现状约束（已核实）：
- `frontend/src/components/shared/ui/` 仅有 `badge / button / card / scroll-area / sonner / utils`——**没有 Radix Dialog/Dropdown 原语**。
- 既有 Modal 模式是**手写**的：`LoginModal.tsx` 用 `{ isOpen, onClose }` props + `fixed inset-0 z-50` 遮罩 + 背景点击关闭 + 右上 X 按钮 + `if (!isOpen) return null`。
- `TopBar.tsx` 右上角现为：登录态显示静态 `UserCircle + username`（非链接），右侧独立「로그아웃」按钮；游客态显示「로그인」按钮（触发 `LoginModal`）。
- App 路由（`frontend/src/app/`）无 `/settings`、`/mypage`、`/profile`。
- 鉴权为服务端会话（cookie/CSRF）；`isAuthenticated` 来自 Redux `s.user.isAuthenticated`。

## Goals / Non-goals

**Goals**
- 主页不再呈现 Pushover 订阅框。
- 登录用户经右上角用户名下拉 →「개인설정」→ 弹窗访问订阅管理。
- 复用既有手写 Modal 模式与既有 `pushSubscriptions.api.ts`，零后端/DB 改动。

**Non-goals**
- 不建独立 `/settings` 路由页（弹窗承载即可）。
- 不引入 Radix/headless-ui 等新依赖。
- 不改 `PushSubscriptionManager` 内部逻辑/状态机/API 调用。

## Decisions

### D1：弹窗 vs 独立路由页 → 选弹窗
用户已选「顶栏下拉 + 设置弹窗」。弹窗与既有 `LoginModal` 一致、零新路由、最小改动。未来如需更多设置项再升级为 `/settings` 页面（已记入 Non-goals，不阻塞）。

### D2：SettingsModal 沿用 LoginModal 手写模式
新建 `frontend/src/components/settings/SettingsModal.tsx`：
- props：`{ isOpen: boolean; onClose: () => void }`，`if (!isOpen) return null`。
- 结构：`fixed inset-0 z-50 flex items-center justify-center p-4` + `absolute inset-0 bg-black/30 backdrop-blur-sm`（点击 `onClose`）+ 居中白卡（`max-w-lg`）+ 右上 X 按钮 + 标题「개인설정」。
- 内容：白卡内 `scroll-area`（订阅列表会增长，避免溢出）包 `<PushSubscriptionManager />`。
- 不在此重复 `isAuthenticated` 门控——`PushSubscriptionManager` 自带门控；且 TopBar 仅对登录态渲染入口（双重保险）。

### D3：TopBar 用户名下拉
- 把 `TopBar.tsx:69-74` 的静态用户名块改为**下拉触发按钮**（`UserCircle + username + ChevronDown`）。
- 新增 state：`isMenuOpen`、`isSettingsOpen`。
- 下拉面板（绝对定位于触发按钮下方）两项：
  - 「**개인설정**」→ `setIsSettingsOpen(true); setIsMenuOpen(false)`。
  - 「**로그아웃**」→ 复用现有 `handleLogout`（登出从原独立按钮**收进下拉**）。
- 关闭交互：点击外部关闭下拉。沿项目无新依赖原则，用 `useEffect` 注册 `document` 的 `mousedown` 监听 + `useRef` 容器判定（或一层透明 backdrop）。组件已是 `'use client'`，可用浏览器 API。
- **游客态保持不变**：`isGuest` 时仍渲染原「로그인」按钮，无下拉、无 SettingsModal 入口。
- 文件底部渲染 `<SettingsModal isOpen={isSettingsOpen} onClose={() => setIsSettingsOpen(false)} />`（与既有 `<LoginModal .../>` 并列）。

### D4：HomePage 止血
- 删除 `HomePage.tsx:7` 的 `import { PushSubscriptionManager } ...`。
- 删除 `HomePage.tsx:142-144` 的 `<div className="mt-..."><PushSubscriptionManager /></div>`。
- 该 `<div>` 删除后右栏 sticky 卡片为该列唯一子项，视觉收尾即可（必要时清理多余 wrapper 间距）。

### D5：文档收尾
- 更新 `PushSubscriptionManager.tsx` 第31行陈旧注释（「홈/설정 영역에 카드로 끼워 쓴다」）为「개인설정 모달 전용」，避免后人再塞回主页。组件逻辑不动。

## Risks / Trade-offs

- **下拉的可访问性/点击外部关闭**：手写下拉需自行处理点击外部关闭与（可选）Esc/焦点管理。本次以"点击外部关闭"为底线；键盘可达性按既有 `LoginModal` 同等水平即可，不过度工程。
- **z-index 叠放**：TopBar 容器为 `z-10`，下拉面板需高于同栏内容；SettingsModal 用 `z-50`（与 LoginModal 一致）覆盖全屏，无冲突。
- **无新依赖**：刻意不引 Radix，换来少量手写交互代码——与现有 `LoginModal` 风格一致，可维护性可接受。

## Migration / Verification

- 纯前端，无 schema/数据迁移。
- 验证（本机无 node，用 docker `node:20` DooD）：
  1. 前端 `lint + build` 跑通（提交前还原 `next-env.d.ts`）。
  2. 视觉/手测：登录后主页底部无 Pushover 框；右上角用户名 → 下拉 →「개인설정」→ 弹窗内出现订阅卡片；注册/启停/해지 一条链路可用；游客态无下拉、仍为「로그인」。
