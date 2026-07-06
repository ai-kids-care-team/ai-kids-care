---
globs: backend/**, frontend/**, ai/**
disclosure: path-scoped
---

# 跨组件契约

组件块只留自己那半边，协议本体归本文件。改任一契约须同步两侧。

## REST 基线

所有业务 API 在 `/api/v1/**`，Cookie 会话 + CSRF 头（非 Bearer）。分页 Spring `Page` ↔ 前端 `PageResponse`。

- **REST 路径命名不统一**：`detection-events`（连字符）vs `detection_sessions` / `cctv_cameras`（下划线）——以实际 controller 为准。

## AI → backend ingest

`POST /api/v1/internal/detection-{sessions,events}`，**Bearer `AI_SERVICE_TOKEN`（ROLE_AI_SERVICE）**；payload camelCase 两侧对齐；幂等键 `dedupKey="{streamId}-{epochSec}"` 按 `(kindergarten_id, dedup_key)` 去重。**backend 是 detection_events 唯一写入者**（ADR-0026）。

> AI 侧客户端行为（有界重试 3 / 指数退避 / 10s 超时）见 `ai.md`。

## SSE 线协议

`GET /api/v1/detection-events/stream`（text/event-stream，会话认证 + 租户域），前端原生 `EventSource`。事件名 `detection-event`，**心跳 25s / 流寿命 30min / Last-Event-ID replay 上限 200**。

- **Caddy 全局 `encode gzip` 会缓冲 SSE 帧**，最坏延迟退化到心跳间隔（25s）；SSE 路径需在 Caddy 排除 gzip。

> SSE 服务端实现（进程内注册表、单实例假设）见 `backend.md`。

## enum 单一真源

`GET /api/v1/enums/{name}`（ADR-0013 退役 `common_codes`/`menus`，label 归前端 i18n）；改 enum 须同步 DB / 后端 `type.*` / 前端三处。

## 内部事件链

ingest → `DetectionEventIngestedEvent` → SSE 推送；review → `EventReviewedEvent`(AFTER_COMMIT) → `GuardianNotificationService` 家长通知。

## 通知渠道

PUSH(Pushover via `push_subscriptions`) + SMS(Solapi via `users.phone`)；EMAIL 已注册未实现。教职员告警 ingest 后即发；家长通知须复核后（ESCALATED 穿透 / RESOLVED 受 quiet_hours 约束，DEFERRED 由 `@Scheduled` 60s 扫描器补发）。
