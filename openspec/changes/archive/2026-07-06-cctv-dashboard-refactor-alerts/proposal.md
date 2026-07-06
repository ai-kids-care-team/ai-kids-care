## Why

两个同文件 finding（`CctvDashboardPage.tsx`，全仓最大文件 1369 行）：

- **QLT-01 / ARC-04（HIGH/MED）**：单文件 God Component，15+ useState、大量 useMemo、10+ 渲染块、模块级格式化/severity 函数全挤在一个文件，无子组件拆分。最高复杂度文件在 C1 前建立测试基建后仍无测试网。
- **UX-02 告警侧（HIGH）**：`load()` 里 `setEvents([])`（:348）**无条件清空**，全文件**无任何 `EventSource`/SSE 订阅**（已确认，唯一 detection 提及是 :253 注释）→ 瓦片告警覆盖层、底部通知面板、`activeAlertCount`、severity 徽章**永久渲染空数组**。旗舰「실시간 모니터링」大屏对真实告警是死的。

侦查确认可低成本复用现成模式：`useDetectionEventStream`（`components/detectionEvents/useDetectionEventStream.ts`）是通用 hook（原生 `EventSource`、事件名 `detection-event`、断线自动重连、Last-Event-ID 浏览器原生回放、cleanup 齐全），`DetectionEventsDashboard.tsx` 的 `onLive` 去重/prepend/上限/高亮模式可直接照搬。

## What Changes

- **QLT-01/ARC-04 拆 God Component**（行为等价重构）：
  - 抽格式化/severity 到 `lib/`：`cctvFormat.ts`（`formatRelativeMinutes`/`formatOverlayTime`/`displayCameraCode`/`displayLocationLine`/`inferCategoryFromCameraName`）、`severity.ts`（`severityLevel`/`severityClasses`，**与 `DetectionEventsDashboard` 共享**，消除两页 severity 展示不一致的小债）。
  - 抽 hooks：`useKindergartenName(id)`、`useCctvCameras(kindergartenId, canView)`（合并 `load()` 相机部分 + `loadCameraStreams`）、`useCctvGridData(...)`（大量派生 useMemo）、`useQuickPlaylist(filteredCameras)`。
  - 拆子组件：`CctvSidebar` / `CctvCameraGrid` + `CctvCameraTile` + `CctvTileAlertList` / `CctvControlPanel` / `CctvAlertPanel` / `CctvFullscreenPlayer` / `CctvCameraDetailModal`。`CctvDashboardPage` 收敛为编排容器。
  - **顺手删死代码**：demo 占位模块（:40-107，`CCTV_TRAFFIC_PLACEHOLDER_ENABLED`/`CCTV_CAMERA_TILE_DUMMY_ENABLED` 两开关均已 `false`，注释自承可删）。
- **UX-02 告警侧接线**（复用现有 SSE）：
  - `load()` 的 `setEvents([])` 换成**真实近期告警 REST 初拉**（参照 `DetectionEventsDashboard.tsx:86-103`）+ **`useDetectionEventStream` 增量订阅**（`onLive` 照搬去重/prepend/`MAX_CARDS`/高亮模式）。
  - `enabled = canViewLiveStreams`（:255，仍限 KINDERGARTEN_ADMIN，避免其它角色发被拒 SSE）；`reconnectKey = sessionKindergartenId`。
  - 统一告警类型：CCTV 页现用精简 `DetectionEventVO`（`types/cctv.vo.ts`）vs SSE 的 `DetectionEventListItem`（`detectionEvents.api.ts`）——选信息更全的 `DetectionEventListItem` 或做映射，二者取一并在 notes 说明。
- **补测试**（C1 基建可用）：抽出的 `lib/severity.ts`/`cctvFormat.ts` 纯函数 + 关键 hook 的单测（severity 分档边界、告警去重/上限逻辑）。

## Non-goals（决策门/超范围）

- **不做人类视频播放路径**（HLS/WebRTC 代理，`:1357` "재생 주소는 공개 API에서 제공하지 않습니다" 是**有意的安全限制**，属决策门 **D-VIDEO**）——本 change 只做**告警侧**，视频侧维持现状。
- **不改可见角色范围**（`canViewLiveStreams` 仍限 KINDERGARTEN_ADMIN；给 TEACHER 本班域视图属全栈+拍板，超范围）→ **不动 `menu.ts`**。
- 不改后端/SSE 线协议/detection 端点（纯前端接线既有 `GET /api/v1/detection-events/stream` + 读 API）。
- 不改 evidence/证据展示（属 INT-02/C7，决策门 D-STORE）。
