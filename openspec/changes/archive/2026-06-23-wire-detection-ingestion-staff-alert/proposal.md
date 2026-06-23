## Why

检测闭环架构已更正定稿（archive: correct-detection-closed-loop-architecture）：AI 经后端 REST 内部端点提交检测、后端独占写库、staff 在 ingest 时即时告警、家长必经复核。本 change 实现该闭环的**第一步、也是解锁规则引擎的起点**：后端检测摄入端点 + staff 即时告警。

完成后:AI(`alarm_on`)→ 后端摄入 → detection 行落库 → 相关 staff 立即收 Pushover + 站内通知，被提醒上系统复核。这把「detection_events 只有种子数据、无 live 来源」的状态打破,为后续 ②复核工作流 / ③规则引擎→家长 PUSH 提供真实数据入口。

## What Changes

- **两个内部摄入端点**（`/api/v1/internal/**`，既有 `Bearer AI_SERVICE_TOKEN`→`ROLE_AI_SERVICE` 通道，无需 @PreAuthorize，镜像 `StreamCredentialController`）：
  - `POST /api/v1/internal/detection-sessions` —— AI 开流时建 session，返回 `session_id`。
  - `POST /api/v1/internal/detection-events` —— AI `alarm_on` 时提交事件（引用 session_id、event_type 枚举、severity、confidence、start/end、AI 生成的 dedup_key、可选 evidence uri+hash+meta）。
- **dedup_key（schema 变更）**：Flyway 迁移给 `detection_events` 加 `dedup_key` 列 + `UNIQUE(kindergarten_id, dedup_key)`；既有种子行回填唯一值后置 NOT NULL。后端对重复 dedup_key 返回幂等结果（已存在则返回原 event，不重复建）。**BREAKING(schema)**——additive 迁移。
- **detection 行写入用 JdbcTemplate**（设计决策）：显式写 `detection_sessions/detection_events/event_evidence_files` 的全部 NOT NULL 列（含 `kindergarten_id`/`camera_id`/`dedup_key`），**绕开只读实体的写改造**(DetectionSession 不映射 kindergarten_id/camera_id、EventEvidenceFile 不映射 kindergarten_id)，不动 `ddl-auto=validate`。
- **staff 即时告警**(角色制 + 异步)：ingest 事件落库后，解析事件所在园全部 ACTIVE `KINDERGARTEN_ADMIN`+`TEACHER`(查 `user_role_assignments`，非 notification_rules)，给每人建 `Notification(channel=PUSH, event_id=该事件)` 并经 `NotificationService.dispatch` 发 Pushover + 落站内可读行。每人 dedupe_key 形如 `evt-{eventId}-u-{userId}-staff`。**@Async** 派发，避免 N 个 staff 顺序 Pushover 阻塞 AI 的 POST（新增 `@EnableAsync`）。
- **请求/响应 DTO**：SessionIngest / EventIngest 请求 DTO（`event_type` 用 `EventTypeEnum`，未知值反序列化即 400）+ 摄入响应 DTO（返回 session_id / event_id）。
- **schema 守卫**：`SchemaConsistencyGuardTest` 增断言 `detection_events.dedup_key` 存在 + 唯一索引；`db/dbml/schema.dbml` 同步加列。

Non-goals(后续步骤)：
- **不**做 SMS(步骤⑤;本期 staff 告警仅 Pushover+站内)、**不**做前端 SSE/WS 看板(步骤⑥)。
- **不**做 event-review 复核工作流(步骤②)、**不**做规则引擎→家长 PUSH(步骤③)、**不**做 AI 端 ingest 客户端(步骤④;AI 改造另算)。
- **不**重构只读 detection 实体为可写(用 JdbcTemplate 规避)。
- 家长通知本期**完全不涉及**(严禁绕过复核)。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `ai-detection`: ADDED「检测摄入 REST 端点(V1)」—— 具体化既有高层闭环 requirement:`/api/v1/internal/detection-sessions`+`/detection-events`(ROLE_AI_SERVICE)、后端独占写、AI 生成 dedup_key 的幂等契约。(notifications「staff 即时告警」已在上个 change 定稿,本 change 仅实现,无 delta。)
- `data-platform`: ADDED「detection_events 幂等键(dedup_key)」—— dedup_key NOT NULL + `UNIQUE(kindergarten_id, dedup_key)`。

## Impact

- **schema**：Flyway `V8__add_detection_event_dedup_key.sql`(ADD COLUMN + 回填 + NOT NULL + UNIQUE)；`db/dbml/schema.dbml` 同步;`SchemaConsistencyGuardTest` 增断言。**高风险 schema 迁移(additive)→ apply 执行前需维护者点头。**
- **产品代码**：新 `internal/DetectionSessionIngestController`+`DetectionEventIngestController`、`DetectionIngestService`(JdbcTemplate 写库 + staff 解析 + 异步告警)、ingest DTO；`@EnableAsync` 配置;复用 `NotificationService.dispatch`/`PushoverService`/`PushSubscriptionRepository`。可能新增 staff 解析 repo 查询。
- **测试**(Testcontainers)：session/event 摄入(ROLE_AI_SERVICE 鉴权、非法 token→401/403、event_type 非法→400、dedup 幂等)、FK 顺序(session 先于 event、event 先于 notification)、staff 告警(本园 admin+teacher 各得 Notification 行 + dispatch 调用;跨园 staff 不收)。
- **CI**：复用 `backend-java-tests.yml`。
- **spec**：ai-detection/notifications/data-platform delta(随 change)。
- **解锁**：步骤②③(复核→规则引擎→家长)有了真实 detection 数据入口。
