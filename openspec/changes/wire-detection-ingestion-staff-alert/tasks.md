## 1. dedup_key schema(additive 迁移)

- [ ] 1.1 Flyway `V8__add_detection_event_dedup_key.sql`：ADD COLUMN dedup_key varchar → 回填种子行 `'seed-'||event_id` → SET NOT NULL → `CREATE UNIQUE INDEX uq_detection_events_dedup (kindergarten_id, dedup_key)`
- [ ] 1.2 `db/dbml/schema.dbml` 同步加 detection_events.dedup_key + 唯一索引
- [ ] 1.3 `SchemaConsistencyGuardTest` 增断言：dedup_key 列存在 + `uq_detection_events_dedup` 存在
- [ ] 1.4 容器内:context 起、validate 通过、schema 守卫绿

## 2. 摄入端点 + 写库（TDD）

- [ ] 2.1 [RED] `DetectionIngestApiTest extends BaseIntegrationTest`：`POST /api/v1/internal/detection-sessions`/`detection-events` —— ROLE_AI_SERVICE 鉴权(带 AI_SERVICE_TOKEN→2xx;缺/错 token→401/403);event_type 非法→400;event 引用不存在 session→4xx;dedup 重复→幂等返回原 event_id
- [ ] 2.2 ingest 请求/响应 DTO（SessionIngest / EventIngest，`EventTypeEnum`；响应含 session_id/event_id）
- [ ] 2.3 `DetectionIngestService`：JdbcTemplate 写 detection_sessions/events(+可选 event_evidence_files) —— 显式全部 NOT NULL 列(kindergarten_id/camera_id/dedup_key…);session 先于 event;dedup `(kindergarten_id,dedup_key)` 幂等(查在先 + DB 唯一兜底)
- [ ] 2.4 `internal/DetectionSessionIngestController` + `DetectionEventIngestController`(`@Hidden`,镜像 StreamCredentialController,委托 service)
- [ ] 2.5 容器内 ingest 测试绿

## 3. staff 即时告警（TDD，角色制 + 异步）

- [ ] 3.1 [RED] staff 告警测试：摄入事件后，本园全部 ACTIVE KINDERGARTEN_ADMIN+TEACHER 各得一条 `Notification(event_id=该事件, channel=PUSH)` 行;跨园 staff 不收;有 active Pushover 订阅者触发 dispatch(打桩/真发)
- [ ] 3.2 staff 解析:`UserRoleAssignmentRepository` 查 ACTIVE+KINDERGARTEN+kgId 过滤 role∈{ADMIN,TEACHER}(或加 IN :roles 查询);**不**用 notification_rules
- [ ] 3.3 `@EnableAsync` + 异步告警方法:ingest 事务提交后触发;每 staff 建 Notification(dedupeKey `evt-{eventId}-u-{userId}-staff`)→ save → `NotificationService.dispatch`;单条失败不影响其余
- [ ] 3.4 容器内 staff 告警测试绿

## 4. 验证与收尾（verification-before-completion）

- [ ] 4.1 容器内 `gradle:8.7-jdk21` 实跑**全套件**全绿(既有 157 + 新增)，留存证据
- [ ] 4.2 范围核对(git diff)：产品改动限于 internal ingest controller/service + ingest DTO + @EnableAsync + V8/dbml + schema 守卫断言;未碰复核/规则引擎/家长/SMS/前端;detection 写用 JdbcTemplate、未重构只读实体
- [ ] 4.3 requesting-code-review;按反馈修正
- [ ] 4.4 合并 develop / push / `/opsx:archive` / 清理 worktree(用户驱动)

---

## ⚠️ 维护者批准项(apply 前确认 —— 含 schema 迁移)

> 本 change 含 **Flyway V8 给 detection_events 加 dedup_key 列 + 唯一索引**(additive,非破坏性,但属 schema 迁移)。**apply 执行 V8 前须经维护者点头。** detection 行写入采用 JdbcTemplate(绕开只读实体改造),不动 `ddl-auto=validate`。
