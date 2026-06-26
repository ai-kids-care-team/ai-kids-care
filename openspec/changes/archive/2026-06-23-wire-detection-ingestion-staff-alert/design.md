## Context

实现纠正后闭环的第一步:后端检测摄入 + staff 即时告警。已核实的关键事实:
- AI↔后端通道既有:`/api/v1/internal/**` + `Bearer AI_SERVICE_TOKEN`→`ROLE_AI_SERVICE`(SecurityConfig + `AiServiceTokenAuthenticationFilter`);`StreamCredentialController`(`com.ai_kids_care.v1.internal`,`@Hidden`)是镜像模板。
- detection 表硬 FK:`detection_events(kindergarten_id,session_id)`→`detection_sessions`;`event_evidence_files(kindergarten_id,event_id)`→`detection_events`;`notifications.event_id` NOT NULL→`detection_events`。
- **只读实体不可直接写**:`DetectionSession` 仅映射 stream_id/model_id(缺 kindergarten_id/camera_id 列映射);`EventEvidenceFile` 缺 kindergarten_id 映射。`detection_events` **无 dedup 列**(dedupe_key 只在 notifications)。
- 无 DB 触发器自动填 kindergarten_id;无 `@EnableAsync`。
- `NotificationService.dispatch(Notification)` 需已持久化的 Notification(event_id NOT NULL);`PushSubscriptionRepository.findByUser_IdAndProviderAndStatus` 取 staff Pushover 地址。
- 无破坏性删除;V8 为 additive。

## Goals / Non-Goals

**Goals:** AI 经两个内部端点提交 session/event(+evidence),后端独占写库(dedup 幂等),并在 ingest 时异步给本园 staff 发 Pushover+站内告警。

**Non-Goals:** SMS(⑤)、前端 SSE 看板(⑥)、复核工作流(②)、规则引擎→家长(③)、AI 端客户端(④)、重构只读实体为可写、任何家长通知。

## Decisions

### D1：detection 行用 JdbcTemplate 写,不重构只读实体
`DetectionIngestService` 用 `JdbcTemplate` INSERT `detection_sessions/detection_events/event_evidence_files`,显式写全部 NOT NULL 列(含 kindergarten_id/camera_id/dedup_key),`RETURNING session_id/event_id` 取主键。
- 理由:只读实体缺 kindergarten_id/camera_id 映射,改它们涉及 JPA 复合 FK 双映射陷阱、且可能扰动读路径 mapper 与 `ddl-auto=validate`;JdbcTemplate 写入零实体改动、列控制精确。
- 取舍:绕过 JPA 类型安全;以 SQL + 测试覆盖弥补。staff 通知仍走实体(`NotificationService`)。

### D2：V8 迁移(additive)
```
ALTER TABLE detection_events ADD COLUMN dedup_key varchar;          -- 先 nullable
UPDATE detection_events SET dedup_key = 'seed-' || event_id WHERE dedup_key IS NULL;  -- 回填种子行
ALTER TABLE detection_events ALTER COLUMN dedup_key SET NOT NULL;
CREATE UNIQUE INDEX uq_detection_events_dedup ON detection_events (kindergarten_id, dedup_key);
```
- initdb 不动(V1 镜像;V8 前进转换两路径)。dbml 同步加列。SchemaConsistencyGuardTest 加断言。

### D3：摄入顺序与幂等
- session 必须先于 event(硬 FK);event 必须先于 notification(notifications.event_id)。同一 `@Transactional` 内顺序写;event 写完即拿到 event_id 再建通知。
- **dedup 幂等**:event 摄入先按 `(kindergarten_id, dedup_key)` 查;已存在 → 返回原 event_id(200,幂等),**不**重复建事件、**不**重复告警。靠 DB 唯一约束兜底竞态(捕获冲突转幂等返回)。
- session 幂等:可按 AI 提供的 session 自然键(camera+started_at 或 AI 端 session ref)做幂等,或每开流建新 session;apply 第 1 步定(倾向 AI 端给 session 自然键)。

### D4：staff 解析 = 角色制
查事件所在园 ACTIVE 的 `KINDERGARTEN_ADMIN`+`TEACHER`:复用 `UserRoleAssignmentRepository.findAllByStatusAndScopeTypeAndScopeId(ACTIVE, KINDERGARTEN, kgId)` 在 Java 内按 role 过滤,或加一个 `IN :roles` JPQL。**不**用 notification_rules(无配置规则的 staff 会被漏掉;规则制留给家长路径)。
- 每 staff 建 `Notification(kindergarten, detectionEvents=该事件, recipientUser=staff, channel=PUSH, title/body, dedupeKey="evt-{eventId}-u-{userId}-staff", status=QUEUED)` → save → `dispatch`(发 Pushover + 落站内可读行;无 active 订阅则 FAILED,不影响其他 staff)。

### D5：异步派发
新增 `@EnableAsync`;staff 告警在 `@Async` 方法里跑(ingest 持久化提交后触发,避免 N×Pushover 阻塞 AI 的 POST,也避免外部调用拖长 ingest 事务)。
- 注意事务边界:先提交 ingest 事务(event 已落库)再异步发通知(通知有自己的事务);异步内每条通知独立,单条失败不影响其余。

### D6：鉴权沿用 ROLE_AI_SERVICE
两端点在 `com.ai_kids_care.v1.internal`,`@Hidden`,无方法级 @PreAuthorize —— 由 `AiServiceTokenAuthenticationFilter` + `requestMatchers("/api/v1/internal/**").hasRole("AI_SERVICE")` 把关。非法/缺 token → 401/403。

### D7：event_type 集中映射在 AI 端,后端只收枚举
DTO 用 `EventTypeEnum`(Jackson 反序列化未知值即 400);映射表仍只在 AI 端(spec 要求),后端不重复。

## Risks / Trade-offs

- [JdbcTemplate 写绕过 JPA 类型安全] → 列名/枚举 cast(`?::event_type_enum` 等)需准;集成测试覆盖每列 + validate 仍守实体。
- [异步通知在 ingest 事务提交后,若进程崩溃丢告警] → 接受(告警尽力而为);事件已落库,复核工作流(②)是权威路径。后续可加重试/outbox。
- [staff 无 push_subscriptions → 收不到 Pushover] → 落了站内 Notification 行(可读)+ 后续 SMS(⑤)兜底;本期接受。
- [V8 对种子行回填 + 唯一索引] → 回填 `'seed-'||event_id` 保证唯一;迁移幂等。
- [高风险 schema 迁移] → additive、可回滚(逆向迁移);apply 执行前维护者点头。

## Migration Plan

1. V8 迁移 + dbml + SchemaConsistencyGuardTest 断言(容器内 validate/守卫绿)。
2. [TDD] 摄入测试(session/event、鉴权、dedup 幂等、FK 顺序)→ DTO + DetectionIngestService(JdbcTemplate 写) + 两 controller。
3. [TDD] staff 告警测试(本园 admin+teacher 得 Notification 行、跨园不收)→ staff 解析 + @EnableAsync + 异步 dispatch。
4. 容器内全套件全绿;spec delta;code review;合 develop / push / archive / 清理 worktree。
- 回滚:逆向 Flyway 迁移删列/索引;代码 git 还原。

## Open Questions

- session 幂等自然键(AI 端 session ref vs camera+started_at)——apply 第 2 步定。
- 异步告警是否要 outbox/重试(防进程崩溃丢告警)——本期接受尽力而为,记 follow-up。
- staff 告警 title/body 文案(韩语?含 event_type + camera + 时间 + 「请上系统复核」)——apply 第 3 步定。
