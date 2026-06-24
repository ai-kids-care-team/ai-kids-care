## Context

**看板现状**（`DetectionEventsDashboard.tsx` + `useDetectionEventStream.ts`）：卡片渲染 `eventType / status badge / severity / detectedAt / cameraName / roomName`；初始经 `GET /api/v1/detection-events?size=20` 拉历史，SSE 实时事件经 `onLive` 按 `eventId` 去重前插、上限 100、新事件高亮 4s。`status` badge 颜色由 `STATUS_BADGE` 表驱动，覆盖 6 个状态。每张卡是 `<li key={e.eventId}>`，整个事件对象在卡片作用域内可用。

**既有复核 API**（闭环 ②，已 archive）：

| Verb | Path | Auth | 说明 |
|---|---|---|---|
| `POST` | `/api/v1/event_reviews` | `EVENT_REVIEW_WRITE`（ADMIN+TEACHER） | 写复核行 + 更新事件 status，发布 `EventReviewedEvent` |

`EventReviewCreateDTO`：`eventId`(必填) / `resultStatus`(必填，非 OPEN) / `comment`(可选) / `affectedChildIds`(可选) / `notifyGuardians`(可选)。响应 `EventReviewVO`（201）。reviewer 身份由会话派生，租户由 `requireActiveKindergartenId()` 约束。**前端目前完全没有 event_reviews 的 service 或 UI**——全新建，但调用形状完全已知。

**状态模型**：`EventStatusEnum = OPEN | ACKNOWLEDGED | IN_REVIEW | RESOLVED | DISMISSED | ESCALATED`；复核可转任意非 OPEN 值，后端无状态机校验（append-only，last-confirm-wins）。

**关键约束**：确认复核**不**经 SSE 重推（SSE 仅推新 ingest 事件），故看板必须**乐观更新**本地状态，否则用户看不到自己刚做的复核生效。

## Goals / Non-Goals

**Goals:**
- 看板卡片内直接确认复核，复用既有 API/DTO。
- 角色门禁（仅 ADMIN/TEACHER）+ 乐观更新 + 失败回滚。

**Non-Goals:**
- 不改后端 API 或行为，不新增端点。
- 不做复核历史/评论列表展示。
- 不做前端状态机校验（后端 YAGNI，保持一致）。
- 不做 `notifyGuardians` 的复杂表单（本期最小：`resultStatus` + 可选 comment；`notifyGuardians` 用既有默认语义）。

## Decisions

- **D1 复用既有端点**：前端只建 thin service + 类型，调 `POST /api/v1/event_reviews`，绝不发明新端点。`EventStatusEnum` 在前端定义为字符串联合，与后端 enum 对齐。
- **D2 乐观更新**：动作成功后立即本地 `setState` 把该卡 `status` 改为 `resultStatus`；失败回滚到原 status + 错误提示。理由：SSE 不推状态变更，乐观更新是唯一能即时反映的方式；备选「成功后重新 GET 单事件」多一次往返且仍需处理竞态，乐观更新 + 失败回滚更顺滑。
- **D3 角色门禁**：`useSelector(s => s.user.user)` 取 `role`，`canReview = role === 'KINDERGARTEN_ADMIN' || role === 'TEACHER'`；后端为权威鉴权（前端门禁仅 UI 降噪）。**不**用 `user-role.ts` 的 `canResolveAnomaly`（其 TEACHER=false 与后端 grant 不一致，已知陈旧），直接判 role。终态（RESOLVED/DISMISSED）卡片隐藏动作。
- **D4 动作集合（最小）**：提供 `RESOLVED` / `ESCALATED` / `DISMISSED` 三个主处置按钮 + 可选 comment 输入。覆盖最常用场景，避免一次堆砌全部 6 状态。
- **D5 React19/Next16 lint 合规**：loading/error/乐观状态的 `setState` **只在按钮 `onClick` 异步回调里**调用，绝不在 effect body 同步调用（`set-state-in-effect`）；不在 render 期读 `ref.current`（沿用看板既有 `timersRef` 仅在 effect/回调中触碰的模式）。

## Risks / Trade-offs

- **乐观更新与后端不一致（后端拒绝/校验失败）** → 失败回滚原 status + toast 提示，用户可重试。
- **多 staff 并发复核同一事件** → 后端 append-only last-wins，UI 最终一致（下次刷新/ingest 校正）；本期不做实时协同。
- **前端 `canReview` 与后端 grant 漂移** → 以后端 401/403 为最终防线；前端门禁仅降噪，按 role 直判避免依赖陈旧的 `canResolveAnomaly`。
- **前端无单测框架** → 本仓前端验证靠 lint + build + 后端契约测试；API 形状已被后端 `EventReviewApiTest` 覆盖，前端不引入新测试框架（见 Open Questions）。

## Migration Plan

纯前端增量，无 schema/后端。回滚 = 移除动作按钮与新 service。

## Open Questions

- 前端是否应引入组件测试框架来覆盖乐观更新/回滚？→ 本仓前端当前无 jest/vitest，引入超出本 change 范围；本期靠 lint+build + 手动验证 + 后端契约测试。若后续统一前端测试基建，再补。
- comment 是否本期必含？→ 含「可选」comment 输入，保持最小；如增噪可降级为后续。
