# Tasks — relocate-push-subscription-to-settings

> 纯前端改动，无破坏性任务（无删表 / 迁移 / schema / 部署），无需维护者逐项预批准。
> 本机无 node：前端 `lint + build` 一律经 docker `node:20` DooD 跑，提交前还原 `next-env.d.ts`。

## 1. SettingsModal（个人设置弹窗）

- [x] 1.1 新建 `frontend/src/components/settings/SettingsModal.tsx`：沿用 `LoginModal` 手写 Modal 模式（`{ isOpen, onClose }` props、`if (!isOpen) return null`、`fixed inset-0 z-50` 遮罩、`bg-black/30 backdrop-blur-sm` 背景点击 `onClose`、右上 X 按钮、标题「개인설정」、`max-w-lg` 白卡）
- [x] 1.2 弹窗内容区用 `scroll-area` 包裹 `<PushSubscriptionManager />`（订阅列表会增长，防溢出）
- [x] 1.3 标注组件为 `'use client'`，import 路径用项目既有 `@/` 绝对路径约定

## 2. TopBar 用户名下拉入口

- [x] 2.1 在 `TopBar.tsx` 新增 state `isMenuOpen`、`isSettingsOpen`，引入 `ChevronDown`（lucide）与 `SettingsModal`
- [x] 2.2 把 `TopBar.tsx:69-74` 的静态用户名块改为下拉触发按钮（`UserCircle + username + ChevronDown`），仅 `!isGuest` 时渲染
- [x] 2.3 下拉面板含两项：「개인설정」→ `setIsSettingsOpen(true)` 并关下拉；「로그아웃」→ 复用既有 `handleLogout`（登出从原独立按钮收进下拉）
- [x] 2.4 实现点击外部关闭下拉（`useRef` 容器 + `useEffect` 注册 `document` `mousedown` 监听；卸载时移除）
- [x] 2.5 游客态保持不变：`isGuest` 时仍渲染原「로그인」按钮、无下拉、无设置入口
- [x] 2.6 文件底部并列渲染 `<SettingsModal isOpen={isSettingsOpen} onClose={() => setIsSettingsOpen(false)} />`

## 3. HomePage 止血

- [x] 3.1 删除 `HomePage.tsx:7` 的 `import { PushSubscriptionManager } ...`
- [x] 3.2 删除 `HomePage.tsx:142-144` 的 `<div className="mt-..."><PushSubscriptionManager /></div>`，并清理因此多余的 wrapper 间距

## 4. 文档收尾

- [x] 4.1 更新 `PushSubscriptionManager.tsx` 第31行陈旧注释（「홈/설정 영역에 카드로 끼워 쓴다」→「개인설정 모달 전용」），逻辑不动

## 5. 验证（docker node:20 DooD）

- [x] 5.1 前端 `lint` 跑通（无新增告警/错误）
- [x] 5.2 前端 `build` 跑通；提交前还原 `next-env.d.ts`
- [ ] 5.3 视觉/手测：登录后主页底部无 Pushover 框；右上角用户名 → 下拉 →「개인설정」→ 弹窗内出现订阅卡片；注册/启停/해지 一条链路可用
- [ ] 5.4 视觉/手测：游客态顶栏无下拉、仍为「로그인」按钮，无任何路径暴露 `PushSubscriptionManager`
