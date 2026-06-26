## Why

闭环步骤③a。步骤②已让 staff 复核 detection 事件(`confirm` 写 `event_reviews` + 更新 `detection_events.status`),但 confirm **明确不发任何通知**——`EventReviewService.confirm` 第 38-40 行标注了「步骤③ hook」待接。`notifications` spec 的「Guardian notification gate on human review」规定「复核确认后 MAY 向 GUARDIAN 派发通知」,但**这条路径没有实现**:家长目前收不到任何通知,闭环断在 staff 复核与家长之间。

本 change 接通核心路径:复核确认(`ESCALATED` 强制 / `RESOLVED` 可选)→ 解析受影响孩子的监护人 → 经现成 `NotificationService.dispatch` 发 PUSH。家长通知由此**端到端贯通**。

`quiet_hours` 静音延后补发需要园级配置存储 + 项目当前完全没有的定时任务基础设施(`@EnableScheduling`/scheduler/ShedLock),拆到 **③b**。本期(③a)所有通知**即时发**:`ESCALATED` 本就穿透静音、不受影响;`RESOLVED` 的静音延后是 ③b 的增量。③a 因此是**纯应用层、零 schema 迁移**。

## What Changes

- **`EventReviewCreateDTO` 增量**:新增 `List<Long> affectedChildIds`(可选,公共空间手动指定受影响孩子)+ `Boolean notifyGuardians`(可选,仅 `RESOLVED` 有意义)。
- **`confirm` 后通知 hook**:复核成功后(`@TransactionalEventListener(AFTER_COMMIT)`,不阻塞/回滚权威的复核事务)调用新建的 `GuardianNotificationService`。触发矩阵:
  - `ESCALATED` → 强制通知家长(③a 不实现静音,即时发);
  - `RESOLVED` 且 `notifyGuardians == true` → 通知;
  - `ACKNOWLEDGED` / `IN_REVIEW` / `DISMISSED` → 不通知。
- **新建 `GuardianNotificationService`**:收件人解析 —— `affectedChildIds` 非空(公共空间)走 `childId → 监护人`;否则走**关系图自动** `room_id + detected_at → ACTIVE class_room_assignment → class → ACTIVE child_class_assignment → children → ACTIVE child_guardian_relationship → guardian.user_id`(走 PostgreSQL/JPA,非 Neo4j)。对每位监护人构建 `Notification`(`channel=PUSH`,`dedupe_key='evt-{eventId}-u-{guardianUserId}-guardian'`,`status=QUEUED`),调 `NotificationService.dispatch`。
- **3 个 repository 时态查询**:`ClassRoomAssignmentRepository`(room+kg+时刻查 ACTIVE)、`ChildClassAssignmentRepository`(class+kg+日期查 ACTIVE children)、`ChildGuardianRelationshipRepository`(child+kg 查 ACTIVE 监护人)。
- **租户**:通知派发是 confirm 的副作用,收件人必属同园(关系图天然按 kindergarten_id 过滤)。

Non-goals(③b 或后续):

- **③b**:`quiet_hours`(园级配置存储 + `deferred_until` 字段 + `DEFERRED` 状态 + `@EnableScheduling`/`@Scheduled` 扫描补发 + ShedLock)。
- **不**接线规则引擎(`notification_rules` 解封 / `min_severity` 过滤)——③ 直接走关系图全量 ACTIVE 监护人;个人规则是独立的 staff opt-in 模型,另议。
- **不**做 SMS(端口未实现,`dispatch` 对非 PUSH 直接 return);**不**做前端实时看板(步骤⑥);**不**做 AI 端 alarm→ingest 客户端(步骤④)。
- **无 schema 迁移。**

## Capabilities

### New Capabilities
（无）

### Modified Capabilities

- `notifications`: ADDED「Guardian notification on review confirmation」—— 实现「Guardian notification gate」所规定的「reviewed → MAY 通知」的**具体路径**:关系图收件人解析 + 触发矩阵(ESCALATED 强制 / RESOLVED 可选)+ 即时 PUSH 派发。明确监护人收件人走**关系图**(room→class→child→guardian),独立于现有「Rule-engine-driven recipient resolution」(那是 staff 个人 opt-in 规则)。

## Impact

- **产品代码**:`EventReviewCreateDTO`(2 字段)、`EventReviewService`(confirm 后 hook / 领域事件)、新 `GuardianNotificationService`、3 个 repository 查询方法。复用 `NotificationService.dispatch` / `PushoverService` / `push_subscriptions`。
- **测试**(Testcontainers):ESCALATED → room 所属 class 孩子的监护人各得 PUSH 行 + dispatch;RESOLVED+notifyGuardians → 通知;RESOLVED 缺省 → 不通知;DISMISSED → 不通知;公共空间 + affectedChildIds → 按指定孩子监护人通知;跨园监护人不通知;重复 confirm 不产生重复通知(dedupe);无 active push_subscription 的监护人记 FAILED。
- **CI**:复用 `backend-java-tests.yml`。**无 schema 迁移、无高风险操作。**
- **spec**:`notifications` delta(随 change)。
- **解锁**:家长通知端到端贯通;③b(quiet_hours 延后补发)在此之上增量。
