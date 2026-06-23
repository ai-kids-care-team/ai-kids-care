## Context

闭环步骤③a。已核实后端现状(为本 change 服务):

- **可复用**:`NotificationService.dispatch(Notification)`(`channel==PUSH` 经 `PushoverService.sendToUser`,`SENDING→SENT/FAILED`;非 PUSH 直接 return);`push_subscriptions`(`provider=PUSHOVER,status=ACTIVE`)查询已有;`notifications` 表含 `UNIQUE(kindergarten_id,dedupe_key)` + `event_id` FK + `NotificationStatusEnum(QUEUED/SENDING/SENT/FAILED/...)`。
- **confirm hook 点**:`EventReviewService.confirm`(line 38-40 注释)当前完全不发通知;`getLatestReview` 预留(denyAll)。
- **关系图 entity 全齐**:`ClassRoomAssignment(room/class/start_at/end_at/status)`、`ChildClassAssignment(child/class/start_date/end_date/status)`、`ChildGuardianRelationship(@EmbeddedId kgId+childId+guardianId, relationship/isPrimary/priority/start_date/end_date)`、`Guardian(user_id FK OneToOne)`。但对应 repository **无时态查询方法**,需新增。
- `DetectionEvent` 有 `room_id` + `detected_at` + `severity` + `status`。
- **项目无任何定时任务机制**(`@EnableScheduling`/`@Scheduled`/ShedLock 全无)→ 故 `quiet_hours` 延后补发不在 ③a。
- `notification_rules` 是**个人规则**(每条含 user_id,「我关注哪个 room/camera」),与监护人关系图解析独立。

## Goals / Non-Goals

**Goals:** 复核确认(ESCALATED 强制 / RESOLVED 可选)→ 关系图(或手动 affectedChildIds)解析监护人 → 即时 PUSH。端到端贯通家长通知。

**Non-Goals:** quiet_hours 静音延后 + scheduler(③b);规则引擎接线;SMS;前端;步骤④。无 schema 迁移。

## Decisions

### D1:hook 用 `@TransactionalEventListener(AFTER_COMMIT)`,不阻塞复核事务
`confirm` 在事务内 publish 一个 `EventReviewedEvent(eventId, resultStatus, affectedChildIds, notifyGuardians, kindergartenId)`;`GuardianNotificationService` 以 `@TransactionalEventListener(phase=AFTER_COMMIT)` 监听,在复核提交**之后**新起事务解析收件人 + 写 notification + dispatch。
- **理由**:复核是权威动作,必须成功并持久化;通知是副作用,其外部 Pushover 调用或解析失败**不应回滚复核**。失败以 `notification.status=FAILED` 记录(可后续重试 / ③b 补发)。
- 权衡:AFTER_COMMIT 下通知失败时复核仍成功(家长没收到但事件已处置)——可接受且符合「复核优先」语义。

### D2:收件人解析 = 关系图 PG 查询链;公共空间用 `affectedChildIds`
- **教室(自动)**:`room_id + detected_at` → `ClassRoomAssignmentRepository` 查 ACTIVE(`start_at<=t AND (end_at IS NULL OR end_at>t) AND status=ACTIVE`)→ class_id(可能多)→ `ChildClassAssignmentRepository` 查 ACTIVE children → `ChildGuardianRelationshipRepository` 查每孩子 ACTIVE 监护人 → `Guardian.user.id`。去重 user_id。
- **公共空间(手动)**:若 `affectedChildIds` 非空,直接以这些 child 走 `child_guardian_relationship → guardian.user_id`(忽略关系图自动链)。
- 走 **PostgreSQL/JPA**,非 Neo4j(图只有 Child→Guardian、无 class_room 时态边)。

### D3:无收件人时**不阻塞** confirm,降级跳过
若需通知但解析不到任何监护人(典型:公共空间事件、`ESCALATED`、但员工未传 `affectedChildIds`;或关系图查空),**该次不发家长通知**、记 WARN 日志,confirm 仍返回 201。
- **理由**:产品决策要求「公共空间 ESCALATED 强制员工先指定孩子」——这更适合作为**前端校验**(提交前必填),后端不应让通知派发阻塞权威复核。后端取降级(不发 + 日志),避免 confirm 因通知解析失败而 4xx。记为可演进点。

### D4:dedupe_key = `'evt-{eventId}-u-{guardianUserId}-guardian'`
同事件、同监护人重复 confirm 不重发(`UNIQUE(kindergarten_id,dedupe_key)` 天然防重)。与 staff 告警的 `'evt-{e}-u-{u}-staff'` 命名区分,互不冲突。

### D5:Notification 构建 + 复用 dispatch
`channel=PUSH`,`status=QUEUED`,`recipient_user_id=guardianUserId`,`event_id`,`title`/`body`(韩文模板,含事件类型/严重度),`dedupe_key` 见 D4。逐个 `dispatch`;无 active push_subscription 的监护人由 dispatch 既有逻辑记 `FAILED`(不抛断其他监护人)。dedupe 唯一冲突时跳过(已存在即视为已发)。

## Risks / Trade-offs

- **[AFTER_COMMIT 通知失败不回滚复核]** 可接受(D1):FAILED 记录 + ③b/重试补。
- **[关系图 N+1]** 多跳查询。用 JPQL join fetch 或按 class/child 批量;监护人量级小(每事件一个班),可接受。
- **[ESCALATED 公共空间无 affectedChildIds]** D3 降级不发 + 日志;前端负责强制指定。记 follow-up。
- **[一个 room 多 class]** D2 设计为「可能多 class」,取孩子并集,不假设 1:1。
- **[event status 在 confirm 时已更新]** hook 读的是 resultStatus(入参),不依赖再查 event.status,避免竞态。

## Migration Plan

1. **[TDD]** 3 个 repository 时态查询 + `GuardianNotificationService` 测试(各触发分支 + 关系图/手动收件人 + 跨园 + dedupe + 无订阅 FAILED)。
2. `EventReviewCreateDTO`(2 字段)+ `GuardianNotificationService`(解析 + 构建 + dispatch)。
3. `EventReviewService.confirm` publish `EventReviewedEvent`;`@TransactionalEventListener(AFTER_COMMIT)` 接入。
4. 容器内 `cleanTest` 全套件全绿;`notifications` spec delta;code review;archive + 合 develop + push。
- **回滚**:纯新增 service + DTO 字段 + repo 查询 + 事件 hook;git 还原;无 schema。

## Open Questions

- `ESCALATED` 公共空间无 `affectedChildIds`:后端降级不发(D3)是否够,还是需返回提示码让前端阻止提交?倾向降级 + 前端校验,apply 时复核。
- 通知 `title`/`body` 韩文文案(占位即可,apply 第 2 步定)。
- hook:领域事件 vs 直接在 confirm 内调用 service —— 倾向 `@TransactionalEventListener(AFTER_COMMIT)`(解耦 + 不阻塞),apply 第 3 步定。

## Follow-ups（code review,本期不阻塞）

- **N1(最有价值)**:端到端集成测试缺失 —— confirm→publish→AFTER_COMMIT `@Async` listener→dispatch 链没有测试覆盖(8 个测试都直接同步调 `notifyOnReview`)。已被人工检视 + EventReviewApiTest 实跑 confirm(含 publish)无回归;建议 fast follow-up 加 `@RecordApplicationEvents` 断言 confirm publish 正确字段,或 HTTP confirm + Awaitility 验证异步派发。
- **N3**:`detectedAt.toLocalDate()` 用存储 offset 推排期查询日期;若 detection 存 UTC 而排期用园本地日期,临界午夜事件可能解析到相邻日 roster。排期多为 `end_date IS NULL` 故实际不受影响,记 ticket。
- **N2**:`ESCALATED` 公共空间无 `affectedChildIds` 静默降级(D3),由前端校验强制员工指定;记 ticket。
