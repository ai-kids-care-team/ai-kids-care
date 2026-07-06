# Tasks — cctv-dashboard-refactor-alerts (C5 / QLT-01 + ARC-04 + UX-02 告警侧)

> 纯 frontend 域，非破坏性。develop 直接提交。**行为等价重构 + 告警接线**。依赖 C1（测试基建，已归档）。

## 1. 抽 lib（纯函数，先补测试再抽，红→绿）
- [x] 1.1 `lib/severity.ts`：`severityLevel`/`severityClasses`（从 CctvDashboardPage :151-166 抽），单测覆盖 ≥7/≥4 分档边界
- [x] 1.2 `lib/cctvFormat.ts`：`formatRelativeMinutes`/`formatOverlayTime`/`displayCameraCode`/`displayLocationLine`/`inferCategoryFromCameraName`（:168-210），单测关键函数
- [x] 1.3 让 `DetectionEventsDashboard` 也用 `lib/severity.ts`（消除两页不一致），确认其现有行为不回归

## 2. 抽 hooks
- [x] 2.1 `useKindergartenName(id)`（合并 :266-316 两个解析 effect）
- [x] 2.2 `useCctvCameras(kindergartenId, canView)`（:333-350 相机部分 + :484-514 streams），去掉 `setTimeout(…,0)` hack
- [x] 2.3 `useCctvGridData(...)`（:359-477 派生 useMemo）
- [x] 2.4 `useQuickPlaylist(filteredCameras)`（:516-566 + 相关 effect）

## 3. 拆子组件（行为等价）
- [x] 3.1 `CctvSidebar` / `CctvControlPanel` / `CctvFullscreenPlayer` / `CctvCameraDetailModal`
- [x] 3.2 `CctvCameraGrid` + `CctvCameraTile` + `CctvTileAlertList`
- [x] 3.3 `CctvAlertPanel`（底部通知面板，告警主要展示位）
- [x] 3.4 `CctvDashboardPage` 收敛为编排容器（组合 hooks + 子组件）
- [x] 3.5 删 demo 占位死代码模块（:40-107，两开关均 false）

## 4. UX-02 告警接线（复用现有 SSE）
- [x] 4.1 统一告警类型（选 `DetectionEventListItem` 或做映射，notes 说明）— 选 `DetectionEventListItem`，见 commit 说明/notes
- [x] 4.2 初拉近期告警 REST（参照 `DetectionEventsDashboard.tsx:86-103`）替换 `setEvents([])`
- [x] 4.3 `import useDetectionEventStream`，`enabled=canViewLiveStreams`、`reconnectKey=sessionKindergartenId`；`onLive` 照搬去重(eventId)/prepend/`MAX_CARDS`/高亮
- [x] 4.4 单测：告警去重 + 上限 + severity 徽章渲染

## 5. 门禁
- [x] 5.1 `cd frontend && npm run test:run` 全绿（含新单测）— 60 passed
- [x] 5.2 `cd frontend && npm run lint && npm run build` 全绿（路由数不减）— 24 routes，0 lint errors（既有 warning 不变）
- [x] 5.3 提交前还原 `next-env.d.ts`
- [ ] 5.4 人工核对：重构后 CCTV 页渲染与重构前一致（相机网格/侧栏/面板），告警面板现显真实 detection 事件（非空）— **未做**：本 worktree 无可跑的后端/DB，只能静态审查 DOM/props 等价性；需 dev-lead 或维护者在有后端的环境里视觉核对一次。
