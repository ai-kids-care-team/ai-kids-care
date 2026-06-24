## Why

闭环 ⑥ 的实时检测看板（commit `f2ec5d6`）目前**只读**：staff 在看板上看到检测事件后，仍需离开看板到别处才能复核处置，拉长了「发现 → 处置」的闭环。后端复核 API（`POST /api/v1/event_reviews`，闭环 ②）已就绪且鉴权为 ADMIN+TEACHER，缺的只是前端把复核动作接到看板卡片上。

## What Changes

- 前端新建 `eventReviews.api.ts`：`confirmEventReview(dto)` 调既有 `POST /api/v1/event_reviews`，附 `EventReviewCreateDTO` / `EventReviewVO` 类型与 `EventStatusEnum` 字符串联合类型。
- `DetectionEventsDashboard` 卡片新增复核动作按钮（最常用处置：`RESOLVED` / `ESCALATED` / `DISMISSED` + 可选 comment），**仅对 KINDERGARTEN_ADMIN / TEACHER 显示**，终态（`RESOLVED`/`DISMISSED`）卡片隐藏动作。
- 成功后**乐观更新**本地卡片 `status`（因为确认复核不会经 SSE 重推状态变更——SSE 只推新 ingest）；失败回滚 + 错误提示。
- CSRF 由既有 axios 拦截器自动处理，无需额外接线。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `ai-detection`: 在既有实时看板能力上，新增「staff 可从看板内直接确认复核」要求（复用既有 event review 工作流 API）。新增独立 requirement，不改既有看板/复核 API 行为。

## Impact

- `frontend/src/services/apis/eventReviews.api.ts`（新建）。
- `frontend/src/components/detectionEvents/DetectionEventsDashboard.tsx`（卡片加动作 + 角色门禁 + 乐观更新）。
- 可能微调 `frontend/src/services/apis/detectionEvents.api.ts` 的 `status` 类型为 `EventStatusEnum | null`。
- **无后端改动**（复用既有 API）、无 schema、无 nginx。
