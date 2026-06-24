## 1. 后端·检测事件读取 API 发布（TDD）

- [x] 1.1 [RED] 读取 API 集成测(Testcontainers):staff `GET /api/v1/detection-events` 返回**本园**事件(最近优先);`GUARDIAN`/匿名 → 403;跨园 `{id}` → 隐藏 404;调用方**不传 kgId**(用 active kindergarten)
- [x] 1.2 `web/DetectionEventController`(`GET /api/v1/detection-events` 列表/最近 N + `GET /{id}` 详情);`DetectionEventService` 去 `@PreAuthorize("denyAll()")` → `@PreAuthorize` staff(`KINDERGARTEN_ADMIN`/`TEACHER`)+ 方法内 `requireActiveKindergartenId()` tenant-scoped;复用既有 `DetectionEventVO`/分页;`SecurityConfig` 落 `authenticated()`(镜像 `NotificationService` 读取范式)

## 2. 后端·SSE 实时推送（TDD）

- [x] 2.1 `event/DetectionEventIngestedEvent`(`eventId`, `kindergartenId`)——进程内 ApplicationEvent
- [x] 2.2 `service/DetectionEventSseService`:per-kindergarten emitter 注册表(`ConcurrentHashMap<Long, Set<SseEmitter>>`,线程安全 Set)+ `register`/`unregister`(`onCompletion`/`onTimeout`/`onError` 全清理)+ `@Async @EventListener onIngested`(取与读取 API **同形 VO**,`send` 到本园 emitter,`IOException`/失败 emitter 即时剔除)
- [x] 2.3 [RED] `DetectionEventSseService` 测:`@EventListener` 收到 event → 对已注册 emitter `send` 正确 payload;死/抛错 emitter 被剔除;只推同园 emitter,不串园
- [x] 2.4 `DetectionEventController.stream()`:`GET /api/v1/detection-events/stream` → `@PreAuthorize` staff + `requireActiveKindergartenId()`;`new SseEmitter(timeout)` 注册 + 立即发 `connected` 帧 + `Cache-Control: no-cache`
- [x] 2.5 [RED] stream 端点鉴权集成测:匿名/`GUARDIAN`/未选园 → 401/403;staff 建流 → 200 `text/event-stream`
- [x] 2.6 `DetectionIngestService`:detection-event 持久化成功且 `!isDuplicate` → `eventPublisher.publishEvent(new DetectionEventIngestedEvent(...))`;[RED/测] ingest → publish(重复 ingest **不** publish);不阻塞 ingest 响应

## 3. 前端·实时看板

- [x] 3.1 `services/apis/detectionEvents.api.ts`:去 throw,接真实 `GET /api/v1/detection-events`(列表/最近 N)+ `/{id}`(详情),匹配后端 VO 字段
- [x] 3.2 `useDetectionEventStream` hook:`new EventSource(url, { withCredentials: true })` + `onmessage` 派发 + 断线重连 + 组件卸载/切换 active kindergarten 时清理重连;流错误/401 优雅回退
- [x] 3.3 看板组件替换 `DetectionEventsUnavailable`:连接前拉最近 N 条历史 + 实时增量(按 `eventId` 去重、新事件高亮/置顶),展示 `event_type`/`severity`/`status`/`detectedAt`/camera/room;`app/detectionEvents` 页面接线(只读 + 可选跳转 `read/{id}`)
- [x] 3.4 `frontend/nginx.conf`:SSE 路径 `proxy_buffering off` + `X-Accel-Buffering: no` + 拉长 `proxy_read_timeout`(防反代攒批)

## 4. 验证与收尾（verification-before-completion）

- [x] 4.1 后端容器 `gradle:8.7-jdk21` DooD `cleanTest test` 全套件全绿(新增 读取/SSE/ingest-publish + 既有零回归),留存证据
- [x] 4.2 前端 `docker node:20` `lint` + `build` 绿(提交前还原 `next-env.d.ts`)
- [x] 4.3 范围核对(git diff):新增 `DetectionEventController`/`DetectionEventSseService`/`DetectionEventIngestedEvent` + `DetectionEventService` 去 denyAll + `DetectionIngestService` publish + 前端 api/hook/看板 + nginx;**无 schema 迁移**;未碰 WebSocket/多实例fanout/evidence/规则引擎/家长看板/复核UI
- [x] 4.4 code review(**opus**)
- [x] 4.5 archive(`ai-detection` spec delta sync)+ commit develop + push

---

> 无 schema 迁移(复用 `detection_events` + 既有读取 + 既有 session 鉴权)。SSE 为单实例进程内 per-kindergarten 注册表;多实例 fanout(Redis pub/sub)、持久化「已投递游标」catch-up、看板内复核操作 记 follow-up。后端验证参考 backend DooD 正确调用(挂 repo 根 + `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` + Ryuk 关);前端参考 docker node:20。
