## Context

闭环步骤⑥,跨后端(Java/Spring Boot 3.2)+ 前端(Next.js 16 / React 19)。已核实现状:

- **ingest 持久化**(`service/DetectionIngestService.ingestEvent`):`jdbc.queryForObject(INSERT INTO detection_events … RETURNING event_id)`,**无 `@Transactional`(autocommit)**,持久化成功后第 ~76 行 `staffAlertService.alertForEvent(…)`(`@Async`)。返回 `DetectionEventIngestResponse(eventId, isDuplicate)`;重复 `(kindergarten_id, dedup_key)` 幂等。→ SSE 推送挂在持久化成功后、且仅非重复时。
- **读取 API 现状**(`service/DetectionEventService`):`listDetectionEvents(kgId, keyword, Pageable)`→`Page<DetectionEventVO>`、`getDetectionEvent(id, kgId)`,均 `@Transactional(readOnly)` + tenant-scoped(`findByKindergarten_Id`/`findByIdAndKindergarten_Id`),但 `@PreAuthorize("denyAll()")` 且**无 Controller**;前端 `services/apis/detectionEvents.api.ts` 两个方法直接 throw「unavailable until tenant authorization exists」。
- **鉴权**:`EffectiveAuthorizationContextHolder.require()` / `requireActiveKindergartenId()`(ThreadLocal,401/403);staff 角色 `KINDERGARTEN_ADMIN`/`TEACHER`(`UserRoleEnum`);`SecurityConfig`:`/api/v1/internal/**`→`hasRole(AI_SERVICE)`,其余 `/api/v1/**`→`authenticated()`;session `IF_REQUIRED` + `DelegatingSecurityContextRepository`(支持 HttpSession);CSRF 对非 internal 的写操作走 CookieCsrfToken(GET 豁免);CORS `allowCredentials=true` 含 localhost:3000。
- **事件解耦模式现成**:`EventReviewService` 用 `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`(③a)。但 ingest **无事务**,故 ⑥ 用普通 `@EventListener`(同步事件,event 已 autocommit)+ `@Async` 不阻塞 ingest。`@EnableAsync` 已开。
- **DetectionEvent 实体**:`id`/`kindergarten`/`cctvCameras`/`rooms`/`detectionSessions`/`eventType`(`EventTypeEnum` 13 值)/`severity`/`confidence`/`detectedAt`/`startTime`/`endTime`/`status`(`EventStatusEnum`:OPEN/ACKNOWLEDGED/IN_REVIEW/RESOLVED/DISMISSED/ESCALATED)/`createdAt`。
- **前端**:`app/detectionEvents/page.tsx` + `read/page.tsx` 均渲染 `DetectionEventsUnavailable` 占位;`apiClient.ts` axios `withCredentials:true` + CSRF 拦截;Redux `userSlice` 有 `kindergartenId`/`role`;`types/detectionEvents.ts` 已定义 `DetectionEventItem` + status union;**无 EventSource/SSE 依赖**;Next 16.1 / React 19.2。
- 无 schema 改动(复用 `detection_events`)。

## Goals / Non-Goals

**Goals:** 发布检测事件读取 API(staff + tenant-scoped);SSE 实时推送(ingest 后进程内 → 该园已连接 staff);前端实时看板(历史 + 增量)。

**Non-Goals:** WebSocket;多实例 SSE fanout;持久化「已投递」追踪式 catch-up scan;evidence;规则引擎;家长端看板;看板内复核操作 UI;schema 迁移。

## Decisions

### D1:SSE 而非 WebSocket
看板是单向 server→client 推送,SSE(Spring `SseEmitter` + 浏览器原生 `EventSource`)最契合:自动重连、走现有 HTTP/cookie 鉴权、无握手/STOMP 负担。

### D2:发布检测事件读取 API(看板历史数据源)
新增 `web/DetectionEventController`:`GET /api/v1/detection-events`(分页/最近 N 条;复用 `DetectionEventService.listDetectionEvents`)+ `GET /api/v1/detection-events/{id}`。把 `DetectionEventService` 的 `@PreAuthorize("denyAll()")` 换成 staff + tenant-scoped——方法内用 `EffectiveAuthorizationContextHolder.requireActiveKindergartenId()` 强制本园,`@PreAuthorize` 限 `KINDERGARTEN_ADMIN`/`TEACHER`(镜像 `NotificationService.listNotifications` 的既有 tenant-scoped 读取范式)。前端 `detectionEvents.api.ts` 去 throw 接真实 GET。

### D3:SSE 端点 + per-kindergarten emitter 注册表
`DetectionEventController.stream()`:`GET /api/v1/detection-events/stream`,`@PreAuthorize` staff,`Long kgId = requireActiveKindergartenId()`,`new SseEmitter(timeout)`,注册到 `DetectionEventSseService`(`Map<Long, Set<SseEmitter>>`,`ConcurrentHashMap` + 线程安全 Set)。生命周期:`onCompletion`/`onTimeout`/`onError` 均 `unregister` 清理;注册后立即发一条 `event: connected`(冲刷头,确认流建立)。落 `SecurityConfig` 的 `authenticated()`;GET 不需 CSRF。

### D4:ingest → 推送解耦(ApplicationEvent + @Async @EventListener)
`DetectionIngestService.ingestEvent` 持久化成功且 `!isDuplicate` 时 `eventPublisher.publishEvent(new DetectionEventIngestedEvent(eventId, kindergartenId))`。`DetectionEventSseService.onIngested` 用 **`@Async @EventListener`**(非 `@TransactionalEventListener`——ingest autocommit 无事务边界)接收:按 `kindergartenId` 取 emitter 集合,`emitter.send(SseEmitter.event().id(eventId).name("detection-event").data(dto))`;`IOException`/失败的 emitter 即时剔除。payload 通过 `DetectionEventService.getDetectionEvent(eventId, kgId)` 取与读取 API **同形的 VO**(前端统一类型)。listener 异常不回传 ingest(已 autocommit + `@Async`)。

### D5:轻量 catch-up = 连接即拉历史
前端进入看板:先 `GET /api/v1/detection-events?size=N`(最近 N 条,默认 N=20)填充列表,再建 `EventSource`;增量事件按 `eventId` 去重合并(防止「拉历史」与「流」窗口内的重复)。spec 的「后端启动 catch-up scan」由此满足看板可见性;**持久化「已投递游标」式补扫**(覆盖「所有客户端都离线时 ingest」)降级为 follow-up——理由:需引入投递状态/schema,且单实例下「下次有人打开看板即拉历史」已覆盖运营需求。design Open Questions 记权衡。

### D6:前端 SSE hook + 看板组件
新增 `useDetectionEventStream` hook:`new EventSource(`${API_BASE}/detection-events/stream`, { withCredentials: true })`(cookie 随流);`onmessage`→解析 → 派发到看板 state;断线浏览器自带重连,另加可见性/卸载清理 + **切换 active kindergarten 时重建连接**;401/流错误回退到轮询或提示。看板组件替换 `DetectionEventsUnavailable`:复用 `types/detectionEvents.ts`,渲染历史列表 + 实时增量(新事件高亮/置顶),展示 event_type/severity/status/detectedAt/camera/room;只读 + 可选跳转 `detectionEvents/read/{id}`。

### D7:nginx SSE 缓冲
`frontend/nginx.conf`:为 `/api/v1/detection-events/stream`(或 SSE 路径)`proxy_buffering off; proxy_cache off; proxy_read_timeout` 拉长 + `X-Accel-Buffering: no`,避免反代缓冲导致「攒批」。后端 stream 响应也带 `Cache-Control: no-cache`。

### D8:鉴权/CSRF/会话
SSE + 读取均走 session cookie(`authenticated()` + method `@PreAuthorize` staff)。GET 不触发 CSRF。tenant 隔离由 `requireActiveKindergartenId()` 在端点内强制(emitter 只按真实 active kgId 注册;推送只发本园),非 staff/未选园 → 403,跨租户不可能(不接受客户端传 kgId)。

### D9:测试策略
- 后端集成(Testcontainers):读取 API tenant scope(本园可读、跨园/非 staff 403);SSE 端点鉴权(匿名/GUARDIAN 拒绝);`DetectionEventSseService` 注册/推送/清理用切片或单元(`@EventListener` 收到 event → 对已注册 emitter `send`,死 emitter 剔除)。`DetectionIngestService` ingest → publish(非重复 publish、重复不 publish)。
- 前端:`useDetectionEventStream` 与看板组件可行则测(jsdom + mock EventSource);至少 lint + build 绿(docker node:20)。

## Risks / Trade-offs

- **[SSE 连接泄漏/超时]** emitter 未清理会累积。缓解:`onCompletion/onTimeout/onError` 全清 + 心跳保活(定时空 comment 帧检测死连接)。
- **[单实例假设]** per-KG emitter 注册表是进程内;多实例下 A 实例 ingest 无法推到连到 B 实例的客户端。本期单 watchtower 可行;多实例 → Redis pub/sub fanout(follow-up)。
- **[历史/流竞态 + 重复]** 前端按 `eventId` 去重;`@EventListener` 同一事件只 publish 一次(非重复 ingest)。
- **[全离线期 ingest 不可见]** D5 不做持久补扫;靠「下次打开拉历史」覆盖。Open Question。
- **[`@Async` listener 取 VO 二次查询]** listener 内 `getDetectionEvent` 再查一次以拿完整 VO(含 camera/room 名)。可接受(低频告警);否则在 event 里带快照。
- **[回滚]** 纯新增 Controller/SSE service/event/listener + 去 denyAll + 前端组件 + nginx;git 还原即可,无 schema。

## Migration Plan

1. **后端读取 API**(D2):`DetectionEventController` GET 列表/详情 + `DetectionEventService` 去 `denyAll`→staff/tenant;[TDD] tenant scope + 鉴权集成测。
2. **后端 SSE**(D3/D4):`DetectionEventIngestedEvent` + `DetectionEventSseService`(注册表 + `@Async @EventListener`)+ `stream` 端点;`DetectionIngestService` publish;[TDD] 鉴权 + ingest→推送 + 清理。
3. **前端**(D5/D6):`detectionEvents.api.ts` 接真实 GET;`useDetectionEventStream`;看板组件替换占位 + 页面接线。
4. **nginx**(D7)。
5. 容器全套件(后端 gradle DooD + 前端 node:20 lint/build)+ code review(opus)+ archive + 合 develop + push。
- 回滚:git 还原;无 schema。

## Open Questions

- **持久化 catch-up scan**:是否后续引入「已投递游标 / outbox」覆盖全离线期 ingest(spec 原文要求);本期「连接即拉历史」替代,follow-up 评估。
- **心跳间隔 / SseEmitter timeout 取值**(如 30s 心跳 / 长 timeout);最近历史 N(默认 20)。
- **emitter 上限 / 背压**:单园连接数上限、慢客户端处理。
- **多实例 fanout**(Redis pub/sub)触发条件(部署扩容时)。
- 看板是否后续内嵌**复核操作**(②的 `event_reviews`)或保持只读 + 跳转。
