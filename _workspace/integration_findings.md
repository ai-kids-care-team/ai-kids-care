# Integration Findings (integration-analyst, sonnet) — 双侧交叉比对

- INT-01 [high｜cross] 公告改一次必丢置顶：后端序列化 `isPinned`，前端 `getAnnouncementForEdit` 读 `d.pinned`（恒 undefined→false）。`AnnouncementVO.java` ↔ `announcements.api.ts:171` + `useAnnouncementsEdit.ts:78`。修：`pinned: d.isPinned ?? false` + 删重复字段。
- INT-02 [high(类型)/低(可见)｜cross] SSE 推 `DetectionEventVO`，前端 cast 成 `DetectionEventListItem`（缺 kindergartenName/cameraName/roomName 声明），类型不健全；列表态混两种 shape。`DetectionEventSseService.java:92` ↔ `useDetectionEventStream.ts:39`。修：统一共享类型。
- INT-03 [medium｜cross(ai)] `build_dedup_key` 用原始字符串 `stream_id`，session body 用 `int(stream_id)`；STREAM_ID 含空白时 int() 吞掉但 dedup key 带空白 → 重连去重失效。`backend_ingest.py:99,180`。修：`str(int(stream_id))` 归一。
- INT-04 [high(生产)｜cross/infra] Caddy `encode gzip` 缓冲 SSE 流，单条告警等满 gzip 块或 25s 心跳才刷 → 实时变最多 25s 延迟。`Caddyfile:19` ↔ `nginx.conf` ↔ `useDetectionEventStream.ts:34`。修：SSE 路由 `encode identity`。
- INT-05 [low｜cross] 前端 `LoginRequest` 有后端 `AuthLoginDTO` 没有的可选 `id` 字段（死类型噪音）。`auth.api.ts:9` ↔ `AuthLoginDTO.java:22`。修：删除。
- INT-06 [low(fragile)｜cross] `dedup_key`（V8 NOT NULL UNIQUE）故意不映射进 `DetectionEvent` 实体；`ddl-auto=validate` 不查未映射列，未来 JPA 写路径会撞 NOT NULL。`DetectionEvent.java:27` ↔ `V8__...sql`。修：映射只读列或加 CI 校验。
- INT-07 [medium｜cross] 后端 `GET /api/v1/notifications`（列表+详情，租户范围）已实现且测试，但前端 `services/apis/` 无任何消费者 → 家长/教师看不到通知历史。`NotificationController.java` ↔ （前端缺文件）。修：建 notifications.api.ts + 收件箱/红点。
- INT-08 [low｜cross] 前端 Redux 存 `kindergartenId` 仅作 SSE 重连 key/UI 门控，后端始终从会话强制 scope（无越权风险）；但 SUPERADMIN 切租户后前端状态会陈旧。修：切租户后重拉 `GET /auth/session`。

Top-3：INT-01（公告静默取消置顶）、INT-04（gzip 致 SSE 实时延迟 25s）、INT-07（通知收件箱后端就绪但前端未接线）。
