# Integration / 边界角度 findings — backend 对外契约（盲测验证跑）

分析对象：backend 整体对外契约的跨边界 shape 一致性。每条 finding 双侧 `file:line` 交叉比对。
范围覆盖：REST 后端↔前端、AI↔后端 ingest、后端↔DB schema/Flyway、SSE/事件协议、配置拓扑、内部鉴权。

汇总：共 10 条。critical 0 / high 3 / medium 3 / low 2 / info 2。
清白边界（已双侧核对一致、记录在文末「verified-clean」）：AI ingest 字段+dedup+event_type 映射、EventReview DTO/VO/enum、DetectionEvent 读 VO、SSE 事件名/重连、enums 端点、auth login/session、CCTV 流 VO。

---

```yaml
- id: INT-01
  angle: integration
  component: cross
  severity: high
  title: 后端 GET /api/v1/notifications（列表+详情）就绪但前端零消费者——通知收件箱未接线
  location: backend/src/main/java/com/ai_kids_care/v1/controller/NotificationController.java:31 ↔ frontend/src/services/apis/（无 notifications*.api.ts；全 frontend/src grep "/notifications" = 0 个 HTTP 调用）
  evidence: |
    后端: @GetMapping List<NotificationReadVO> listNotifications()  (NotificationController.java:31-34)
          @GetMapping("/{id}") NotificationReadVO getNotification(...)  (:36-37)
          NotificationReadVO = {notificationId, title, body, status, createdAt}
    前端: services/apis 下无 notification 文件；`grep -rIl "/notifications" frontend/src` 命中 0。
  description: 闭环通知链（规则引擎→dispatch→投递）后端完整且测试覆盖，但家长/教师在 UI 上没有任何入口读取自己收到的通知历史。审计轨迹与告警送达对最终用户完全不可见——契约「后端就绪一侧、前端缺失一侧」。
  recommendation: 新增 notifications.api.ts（消费 GET /api/v1/notifications + /{id}，类型对齐 NotificationReadVO 五字段），加收件箱/红点组件；补一条「前端调用→后端返回 shape」契约测试。
  confidence: high
  cross_refs: [INT-02]

- id: INT-02
  angle: integration
  component: cross
  severity: high
  title: push_subscriptions 全 CRUD（POST/GET/PUT/DELETE）就绪但前端无注册流——Web Push 订阅未接线
  location: backend/src/main/java/com/ai_kids_care/v1/controller/PushSubscriptionController.java:36-52 ↔ frontend/src（grep "push_subscriptions|serviceWorker|VAPID|registerPush" = 0）
  evidence: |
    后端: @PostMapping / @GetMapping / @PutMapping("/{id}") / @DeleteMapping("/{id}")  暴露完整订阅 CRUD。
    前端: grep -rniE "push_subscriptions|pushSubscription|serviceWorker|VAPID|registerPush" frontend/src → 无命中。
  description: 家长 PUSH 通道（notifications③ 设计）依赖浏览器订阅 endpoint 写入后端。后端订阅表与 CRUD 已落地，但前端没有 Service Worker / 订阅注册逻辑去 POST 订阅，也没有管理 UI。即便后端能 dispatch，浏览器侧无订阅→ PUSH 永远投不到家长。整条家长 PUSH 边界单侧悬空。
  recommendation: 前端实现 Service Worker + PushManager.subscribe，将订阅 POST /api/v1/push_subscriptions；登出/失效时 DELETE。与后端订阅 DTO 字段（endpoint/keys）逐字段对齐后加契约测试。
  confidence: high
  cross_refs: [INT-01]

- id: INT-03
  angle: integration
  component: cross
  severity: high
  title: 多组件读端点（children/classes/rooms/ai_models/detection_sessions/guardians/teachers）后端 GET 就绪，前端 api 被故意 stub（return null/throw），功能边界悬空
  location: backend controllers（ChildrenController.java:30 / ClassController.java:26 / RoomController.java:26 / GuardianController.java / TeacherController.java）↔ frontend/src/services/apis/{children,guardians,teachers}.api.ts（stub）
  evidence: |
    前端 children.api.ts:7  throw new Error('Child profile search is unavailable until relationship authorization exists')
    前端 guardians.api.ts:13 getGuardianByUserId → `void userId; return null;`
    前端 teachers.api.ts:34  getTeacherByUserId → `return null;`  :103 searchTeachers → 返回空 page  :123 getTeacher → throw
    后端: /api/v1/children, /api/v1/guardians, /api/v1/teachers, /api/v1/classes, /api/v1/rooms 均有 GET 端点（无前端调用方）。
  description: 这些读端点后端实现+受控（租户授权），但前端客户端被显式 stub 成 null/empty/throw，等待「relationship/tenant authorization」。属边界「后端就绪、前端主动断线」。后果：依赖这些（如家长选择子女、教师名查找、班级/教室列表）的用户旅程拿不到真实数据，且 stub 静默返回 null 易被上层误当「无数据」。
  recommendation: 按授权设计补全前端 api（apiClient.get 对应端点），或在 stub 处显式标注 TODO + 关联 spec；至少把「return null」改成可观测的未实现告警，避免被当正常空集。lead 评估这是分阶段交付的已知缺口还是回归。
  confidence: high
  cross_refs: [INT-08]

- id: INT-04
  angle: integration
  component: cross
  severity: medium
  title: 前端 CctvCameraStatus 状态联合类型缺 REJECTED——与后端 StatusEnum / 迁移后 DB status_enum 不同步
  location: frontend/src/types/cctv.vo.ts:5 ↔ backend/src/main/java/com/ai_kids_care/v1/type/StatusEnum.java:4 ↔ db V2__admin_audit_schema.sql:8
  evidence: |
    前端: export type CctvCameraStatus = 'ACTIVE' | 'PENDING' | 'DISABLED';   (3 值)
    后端: enum StatusEnum { ACTIVE, PENDING, DISABLED, REJECTED }            (4 值)
    DB:   initdb status_enum = 3 值; V2: ALTER TYPE status_enum ADD VALUE IF NOT EXISTS 'REJECTED'  → 运行时 4 值
  description: status_enum 在三处中：后端 enum 与「迁移后」DB 都是 4 值（含 REJECTED），但前端联合类型只有 3 值。任何返回 status=REJECTED 的实体（用户/会员/角色审批拒绝态共享同一 status_enum）到前端会落到联合类型之外，TS 类型保证失效、状态映射/徽章渲染可能漏判。CCTV 相机本身或许不进 REJECTED，但该联合类型被当作通用相机状态、且同源枚举在审批流确实写 REJECTED。
  recommendation: 前端联合类型补 'REJECTED'（或抽出单一 StatusEnum 联合供全局复用）；建一个「后端 enum → 前端联合」的生成/校验脚本，防再次三处漂移。
  confidence: high
  cross_refs: [INT-05]

- id: INT-05
  angle: integration
  component: db
  severity: low
  title: db/initdb 与 db/dbml 基线 schema 的 status_enum 仅 3 值，落后于「真值=迁移后 4 值」，作为 source-of-truth 失真
  location: db/initdb/01_create_schema.sql:1-5 ↔ backend/src/main/resources/db/migration/V2__admin_audit_schema.sql:8（ADD VALUE REJECTED）
  evidence: |
    initdb 01_create_schema.sql:1  CREATE TYPE "status_enum" AS ENUM ('ACTIVE','PENDING','DISABLED');  -- 无 REJECTED
    V2 才补: ALTER TYPE status_enum ADD VALUE IF NOT EXISTS 'REJECTED';
    （notification_status_enum 同理：initdb 7 值，V9 才 ADD 'DEFERRED'）
  description: 运行/测试态正确（Flyway 在 initdb 之上叠加，最终 4 值；seed 即 testcontainer fixture）。但「裸读 initdb / dbml」会得到过时枚举集，作为人/工具理解 schema 的事实源已失真。属文档/源真相漂移，非运行缺陷。
  recommendation: 在 initdb 与 dbml 的 status_enum / notification_status_enum 处加注释指向 V2/V9 的最终值集，或（若策略允许 initdb 镜像最终态）同步补值；明确「initdb=V1 镜像，最终态见 migration」约定。
  confidence: high
  cross_refs: [INT-04]

- id: INT-06
  angle: integration
  component: infra
  severity: medium
  title: docker-compose.yml 无 ai 推理/告警服务——AI→后端 ingest 链在容器拓扑中无生产者
  location: docker-compose.yml:1-132（services: db/neo4j/redis/data-loader/backend/frontend，无 ai）↔ ai/scripts/stream_live_alert_service.py:241,422（create_session/submit_event 调用方）
  evidence: |
    compose services: db, neo4j, redis, data-loader, backend, frontend —— 无 ai 服务定义。
    实际 ingest 生产者是 ai/scripts/stream_live_alert_service.py（脚本，非 compose 服务），
    依赖 java_backend_url + ai_service_token（AI_SERVICE_TOKEN）。后端侧 AI_SERVICE_TOKEN 在 compose backend env 已就绪（docker-compose.yml:107）。
  description: 后端内部 ingest 端点（/api/v1/internal/detection-*）+ Bearer 鉴权 + AI_SERVICE_TOKEN 注入都对齐就绪，但 compose 编排里没有任何服务去调用它们——AI 推理/告警以脱离编排的脚本形式存在。拓扑契约上「消费者已配 token，生产者未编排」，端到端闭环在标准 compose 起不来（需手工跑脚本）。
  recommendation: 若 AI 服务应随栈起：在 compose 增加 ai 服务（镜像/build + JAVA_BACKEND_URL + AI_SERVICE_TOKEN + depends_on backend healthy）。若有意脱编排：在 README/runbook 明确「ingest 生产者为外部脚本」，并把 stream_live_alert_service.py 的运行方式文档化，避免被当作缺失接线。lead 判定属设计选择还是遗漏。
  confidence: high
  cross_refs: []

- id: INT-07
  angle: integration
  component: cross
  severity: medium
  title: AI submit_event 的 start_time 与 end_time 同时取 now_iso，告警窗口时长在边界处归零
  location: ai/scripts/stream_live_alert_service.py:421,429,430 ↔ backend DetectionIngestService.java:69（start_time,end_time 入库）+ db detection_events.start_time/end_time(NOT NULL)
  evidence: |
    AI: now_iso = datetime.now(timezone.utc).isoformat()  (line 421)
        submit_event(..., now_iso, now_iso, ...)            (lines 429-430，start=end)
    后端: INSERT ... start_time=?, end_time=?  (DetectionIngestService.java:69) 原样入库
  description: AI 把同一瞬时同时塞进 startTime 与 endTime，后端忠实持久化 → detection_events 每行 end_time==start_time，事件时长恒为 0。前端 DetectionEventVO 暴露 startTime/endTime（detectionEvents.api.ts:17-18），任何「持续时长/时间轴」展示都会显示 0。契约 shape 对齐，但语义在 AI 边界处坍缩。
  recommendation: AI 端用真实告警窗口起止（onset_epoch ↔ 当前）填 start/end，而非同一 now_iso；或后端/前端明确 start==end 的语义。补一条断言 end_time>=start_time 的 ingest 测试。
  confidence: medium
  cross_refs: []

- id: INT-08
  angle: integration
  component: frontend
  severity: low
  title: teachers.api normalizeTeacherVO 同时兼容 camelCase 与 snake_case，暗示对后端响应 shape 的不确定（且当前 stub 下为死路径）
  location: frontend/src/services/apis/teachers.api.ts:63-93（normalizeTeacherVO 读 raw.kindergartenId || raw.kindergarten_id || r.kindergarten_id …）↔ backend TeacherController VO（Jackson 输出纯 camelCase）
  evidence: |
    前端: kindergartenId = firstPositiveLong(raw.kindergartenId, raw.kindergarten_id, r.kindergarten_id) ?? 0;
          userId = firstPositiveLong(raw.userId, raw.user_id, r.user_id) ...  (多字段双形态兜底)
    后端: VO record 经 Jackson → 一律 camelCase（如 DetectionEventVO/CameraStreamVO 已验证 camelCase）。
    且 searchTeachers/getTeacher 当前 stub（return empty/throw），normalizeTeacherVO 实际无真实数据流入。
  description: 防御性双形态归一化说明实现者对后端究竟返回 camel 还是 snake 不确定——这是边界契约未被钉死的信号。当前因 stub 不触发，但一旦接线，若后端确为 camelCase，则 snake 分支永不命中（死代码）；若哪天某端点漏配 Jackson 命名策略返回 snake，又会静默「看似工作」掩盖真问题。
  recommendation: 钉死后端响应命名（确认全局 PropertyNamingStrategy=camelCase），前端去掉 snake_case 兜底；接线 teachers 端点时加一条「真实后端 JSON → 前端类型」契约测试锁定 shape。
  confidence: medium
  cross_refs: [INT-03]

- id: INT-09
  angle: integration
  component: cross
  severity: info
  title: 前端 SSE EventSource 用 withCredentials=true，依赖后端 CORS allowCredentials + 同源/允许源——跨域部署下需核对
  location: frontend/src/components/detectionEvents/useDetectionEventStream.ts:34 ↔ backend Security/CORS 配置 + DetectionEventController.java:57(/stream, text/event-stream)
  evidence: |
    前端: new EventSource(url, { withCredentials: true })  (useDetectionEventStream.ts:34)
          url = `${API_BASE_URL}/detection-events/stream`   (:33)
    后端: SSE 端点存在；rely on session cookie 鉴权 + @PreAuthorize（DetectionEventController.java:57-58）。
  description: EventSource 带凭据要求后端 CORS 对该源 allowCredentials=true 且 Access-Control-Allow-Origin 非通配。同源（同 host:port，经反代）下无问题；若前端与后端跨域部署，凭据型 SSE 会被浏览器拦截，看板实时增量静默失效（onerror→不断重连）。本次未读到 CORS 具体配置，标 info 供 security/infra 核对。
  recommendation: 确认部署拓扑（反代同源 vs 跨域）。跨域则核对 CORS allowCredentials + 显式 allowedOrigins 含前端源；同源则在 runbook 注明依赖反代同源以免误配。
  confidence: low
  cross_refs: []

- id: INT-10
  angle: integration
  component: cross
  severity: info
  title: AI build_dedup_key 用「调用时 time.time()」而非真实 alarm onset，跨秒去重语义弱于注释承诺
  location: ai/scripts/stream_live_alert_service.py:420,431 ↔ ai/src/ai_app/utils/backend_ingest.py:174-180 ↔ backend dedup（DetectionIngestService.java:53, uq (kindergarten_id,dedup_key)）
  evidence: |
    AI: onset_epoch = time.time()  (stream_live_alert_service.py:420，紧贴 submit 前取)
        build_dedup_key(stream_id, onset_epoch)  (:431)
    util 注释: "same alarm window (or a reconnect/debounce retry of it) yields the same key"  (backend_ingest.py:175)
    后端: findByDedup(kindergartenId, dedupKey) 幂等（DetectionIngestService.java:53,144）。
  description: dedup_key = f"{stream_id}-{int(epoch)}"（秒精度）。但 onset_epoch 是「即将提交时」的 time.time()，非真实告警起始瞬时；同一告警窗口的两次提交若跨过整秒边界会得到不同 key → 后端视为新事件、去重不触发。注释承诺的「同一窗口/重连重试同 key」仅在同一整秒内成立。属去重语义边界的弱化，非破坏（多数实现有冷却，跨秒重复概率低）。
  recommendation: 用真实 alarm onset 时间（窗口起点，而非提交时刻）构造 dedup_key；或文档化「秒级、同窗口同秒内幂等」的实际保证，校正 util 注释。
  confidence: low
  cross_refs: [INT-07]
```

---

## Verified-clean 边界（双侧已交叉比对一致，无 finding）

1. **AI ingest payload ↔ 后端 DTO**（INT 重点）：
   - `backend_ingest.submit_event` body `{sessionId, eventType, severity, confidence, startTime, endTime, dedupKey, evidence?}` (backend_ingest.py:154-164)
     ↔ `DetectionEventIngestRequest {sessionId, eventType, severity, confidence, startTime, endTime, dedupKey, status?, evidence?}` (DetectionEventIngestRequest.java:24-34)。字段名全 camelCase 对齐（与 schema 示例担心的驼峰/下划线错位**相反**——这里两侧都是 camelCase，dedupKey 一致，**无** INT-05 示例那种 bug）。
   - `create_session` `{streamId, modelId}` (backend_ingest.py:99) ↔ `DetectionSessionIngestRequest {streamId, modelId}` (DetectionSessionIngestRequest.java:11-14)；响应 `{sessionId}` ↔ AI 读 `response.json()["sessionId"]` (backend_ingest.py:106)。一致。
2. **event_type 枚举映射 12 AI 标签 → 13 enum**：AI `_LABEL_TO_EVENT_TYPE` 12 标签 + `map_label` unknown→OTHER 兜底 (event_type_mapper.py:14-34) ↔ 后端 `EventTypeEnum` 13 值 ↔ DB `event_type_enum` 13 值 (01_create_schema.sql:84-98) ↔ 前端 `DetectionEventType` 13 值 (cctv.vo.ts:20-33)。**四处完全一致**，OTHER 兜底正确。
3. **EventReview 提交链**：前端 `EventReviewCreateDTO {eventId, resultStatus, comment?, affectedChildIds?, notifyGuardians?}` (eventReviews.api.ts) ↔ 后端 `EventReviewCreateDTO` 同名同型 (EventReviewCreateDTO.java)；前端 `EventReviewVO` ↔ 后端 record (EventReviewVO.java) 8 字段一致；`EventStatusEnum` 6 值前后端一致。
4. **DetectionEvent 读 VO**：前端 `DetectionEventListItem`/`DetectionEventResponse` 17 字段 (detectionEvents.api.ts:4-44) ↔ 后端 `DetectionEventVO` 17 字段同名同序 (DetectionEventVO.java)。一致。
5. **SSE 协议**：事件名 `connected`/`detection-event` 后端 (DetectionEventSseService.java sendEvent / Controller connected frame) ↔ 前端 addEventListener 完全对齐 (useDetectionEventStream.ts:36-37)；心跳 comment 帧（浏览器忽略）；`id:`=event_id 驱动浏览器自动 Last-Event-ID 重连，后端 replaySince 解析对齐 (Controller.java:70-73, replay-max 200)。一致。
6. **enums 端点**：前端请求 gender/guardian_relationship/teacher_level (useSignupForm.ts:516-518) 全在后端 REGISTRY (EnumMetadataService.java:30-36)；响应 `{code, sortOrder}` 两侧一致 (EnumValueVO ↔ frontend EnumValue type:64)。
7. **auth login/session**：前端 `LoginRequest{identifier,password,id?}` ↔ `AuthLoginDTO{identifier,password}`（多余 id 被 Jackson 忽略，无害）；`AuthSessionResponse` 6 字段 ↔ `AuthSessionVO` record 6 字段一致 (auth.api.ts:15-22 ↔ AuthSessionVO.java)。
8. **CCTV 流/相机 VO**：`CameraStreamVO` 14 字段、`CctvCameraVO` 10 字段，前后端逐字段一致 (cctv.api.ts:4-17 / cctv.vo.ts:7-18 ↔ CameraStreamVO.java / CctvCameraVO.java)。
9. **内部 ingest 鉴权**：AI `Authorization: Bearer {AI_SERVICE_TOKEN}` (backend_ingest.py:98,153) ↔ 后端 `AiServiceTokenAuthenticationFilter` 常时间比较 → ROLE_AI_SERVICE，仅作用 /api/v1/internal/**，CSRF 豁免 (AiServiceTokenAuthenticationFilter.java:58-63, SecurityConfig.java:60,99,116)；token 经 `internal.ai.service-token`/AI_SERVICE_TOKEN 注入，compose backend env 已配 (docker-compose.yml:107)。一致（生产者编排缺失见 INT-06）。

## 复核/转交提示
- INT-01/INT-02/INT-03 的鉴权细节（家长读自己通知的越权边界、push 订阅归属租户校验）→ 建议转 **security-analyst** 深挖。
- INT-04/INT-05 枚举三处漂移的结构根因（缺单一 source-of-truth 生成机制）→ **architecture-analyst**。
- INT-06/INT-09 拓扑/CORS → **infra/security** 联合确认部署形态。
