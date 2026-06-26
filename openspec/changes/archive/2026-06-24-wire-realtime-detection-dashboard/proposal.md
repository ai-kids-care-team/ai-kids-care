## Why

闭环步骤⑥(最后一环)。①staff 告警、②event-review 复核、③家长通知、④AI→后端 ingest、⑤SMS 都已发布,但 **staff 仍没有一个实时看到检测事件流的看板**。`ai-detection` spec 已要求「后端 SHALL 在 ingest handler 内通过 SSE/WebSocket 推送实时 detection-event(不用 PG LISTEN/NOTIFY),前端实时接收」(requirement "Backend pushes detection events to the frontend on ingest"),但:

- 后端**零实现**(无 `SseEmitter`/WebSocket);
- 检测事件**读取 API 被屏蔽**(`DetectionEventService.listDetectionEvents/getDetectionEvent` 已实现且 tenant-scoped + 分页,但 `@PreAuthorize("denyAll()")`,无 Controller,前端 `detectionEvents.api.ts` 直接 throw "unavailable until tenant authorization exists");
- 前端检测事件页是 `DetectionEventsUnavailable` 占位(「기능 준비 중」)。

本 change(⑥)兑现该 spec:发布检测事件读取 API(看板历史数据源)+ 实现 **SSE 实时推送**(ingest 后从后端进程内推送到该园已连接的 staff 客户端)+ 前端**实时看板 UI**。

## What Changes

- **后端·发布检测事件读取 API**:新增 `DetectionEventController`(`GET /api/v1/detection-events` 列表/最近 N 条 + `GET /api/v1/detection-events/{id}` 详情);把 `DetectionEventService` 的 `denyAll()` 换成 staff(`KINDERGARTEN_ADMIN`/`TEACHER`)+ tenant-scoped 鉴权(经 `EffectiveAuthorizationContext`,仅本园)。这是看板初始/历史数据源。
- **后端·SSE 实时推送**:`GET /api/v1/detection-events/stream`(返回 `SseEmitter`;复用 session cookie 鉴权 + `@PreAuthorize` staff + `requireActiveKindergartenId()` 做 tenant scope);进程内 **per-kindergarten emitter 注册表**;`DetectionIngestService` 在 detection-event 持久化成功后 `publishEvent(DetectionEventIngestedEvent)`,由 `@Async @EventListener` 推送给该园已连接 emitter。**无 LISTEN/NOTIFY**,后端是唯一 writer 故 ingest 即知。
- **轻量 catch-up**:前端连接看板时**先拉最近 N 条历史**(读取 API),再建 SSE 接增量。spec 的「启动 catch-up scan 补扫未投递」用此「连接即拉历史」方式满足看板可见性需求;后端持久化「已投递游标」式补扫降级为 follow-up(理由见 design)。
- **前端·实时看板**:`detectionEvents.api.ts` 接真实 GET(去 throw);新增 SSE 订阅 hook(原生 `EventSource` + `withCredentials` + 断线重连 + 切换园重连);看板组件替换 `DetectionEventsUnavailable`——展示历史列表 + 实时增量插入,显示 event_type/severity/status/时间/摄像头/房间。
- **nginx**:SSE location 关闭代理缓冲(`proxy_buffering off`),保证流式不被缓冲。

Non-goals:

- **WebSocket**(③决策走 SSE)。
- **多实例 SSE fanout**(单 watchtower 部署;多实例需 Redis pub/sub 广播 → follow-up)。
- 后端**持久化「已投递」追踪式 catch-up scan**(本期用「连接即拉历史」轻量替代)。
- evidence 视频流(④');规则引擎;**家长端看板**(本期 staff 看板);看板内**复核操作 UI**(②已有 `event_reviews` API;本期看板只读展示 + 可选跳转,不内嵌确认动作)。
- 无 schema 迁移(复用 `detection_events` + 既有读取 + 既有 session 鉴权)。

## Capabilities

### Modified Capabilities

- `ai-detection`:实现/细化「Backend pushes detection events to the frontend on ingest」—— 明确 **SSE** 机制、`GET /api/v1/detection-events/stream`(staff + tenant-scoped)、ingest 后进程内 `@Async` 推送、以及「连接即拉最近 N 条历史」作为客户端 catch-up;新增**检测事件读取 API 发布**(列表/详情,staff + tenant-scoped,取代旧 `denyAll` gap)。

## Impact

- **后端**:新 `DetectionEventController`(读取 + SSE stream)、`DetectionEventSseService`(per-KG emitter 注册表 + `@Async @EventListener`)、`DetectionEventIngestedEvent`、`DetectionIngestService`(persist 后 publish);`DetectionEventService` 去 `denyAll` → staff/tenant 鉴权;SSE 端点的 SecurityConfig/CSRF 适配(GET 流,落 `authenticated()`)。
- **前端**:`detectionEvents.api.ts`(接真实 GET)、新 SSE hook、看板组件(替换占位)、`detectionEvents` 页面接线;`nginx.conf` SSE 缓冲。
- **测试**:后端 SSE 端点鉴权(非 staff/跨租户拒绝)+ ingest→推送(Testcontainers/集成)+ 读取 API tenant scope;前端 lint+build(docker node)。
- **CI**:复用 `backend-java-tests.yml` + `frontend-lint-build.yml`;**无 schema 迁移**。
- **解锁**:闭环⑥完成 —— staff 实时看板;检测事件读取 API 上线(其他场景可复用)。
