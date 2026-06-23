## 1. 关系图收件人解析（repository 时态查询，TDD）

- [x] 1.1 `GuardianNotificationServiceTest`(8 scenario:ESCALATED 教室自动 / RESOLVED+notify / RESOLVED 缺省不发 / DISMISSED 不发 / 公共空间 affectedChildIds / 公共空间无指定不发 / dedupe / 无订阅 FAILED)—— 全绿
- [x] 1.2 `ClassRoomAssignmentRepository.findActiveClassIds(roomId,kgId,ACTIVE,at)` JPQL(start_at<=at<end_at)
- [x] 1.3 `ChildClassAssignmentRepository.findActiveChildIds(classIds,kgId,ACTIVE,date)`
- [x] 1.4 `ChildGuardianRelationshipRepository.findActiveGuardianUserIds(childIds,kgId,ACTIVE,date)` → `guardians.user.id`

## 2. DTO + GuardianNotificationService

- [x] 2.1 `EventReviewCreateDTO` 加 `affectedChildIds`(List<Long>)+ `notifyGuardians`(Boolean)
- [x] 2.2 `GuardianNotificationService.notifyOnReview(eventId,kgId,resultStatus,roomId,detectedAt,affectedChildIds,notifyGuardians)`:触发矩阵 + 收件人解析(affectedChildIds 优先/否则关系图)+ 去重 + 构建 Notification(PUSH,`evt-{e}-u-{g}-guardian`,QUEUED)+ dispatch;无收件人 WARN 跳过;镜像 StaffAlertService best-effort
- [x] 2.3 容器内编译 + service 测试通过(1m59s)

## 3. confirm 后通知 hook

- [x] 3.1 `EventReviewService.confirm` publish `EventReviewedEvent`(带 roomId+detectedAt,避 AFTER_COMMIT lazy);`GuardianNotificationService.onEventReviewed` `@Async @TransactionalEventListener(AFTER_COMMIT)` 接入;替换 line 38-40 hook 注释
- [x] 3.2 `EventReviewController` 经 `@RequestBody` 自动贯通 affectedChildIds/notifyGuardians;confirm hook 经全套件 EventReviewApiTest 复绿(无回归);端到端派发由 AFTER_COMMIT listener 触发(notifyOnReview 同步路径已 8 scenario 全测)

## 4. 验证与收尾（verification-before-completion）

- [x] 4.1 容器内 `gradle:8.7-jdk21` DooD `cleanTest test` 全套件全绿:**BUILD SUCCESSFUL in 2m46s**(既有 + 新增 8;新代码单测 1m59s 亦绿)
- [ ] 4.2 范围核对(git diff):仅新增 `GuardianNotificationService` + `EventReviewedEvent` + DTO 2 字段 + 3 repo 查询 + confirm hook;未碰 ③b(quiet_hours/scheduler)/规则引擎/SMS/前端;**无 schema 迁移**
- [x] 4.3 code review(**opus** sub-agent):**Ready to merge,无 Blocking**;AFTER_COMMIT/@Async 语义、3 个 JPQL 时态/租户路径、触发矩阵、dedupe、跨租户、D3 降级逐项核查通过。follow-up 记 design:N1 端到端 publish→listener 集成测试、N3 detectedAt 时区临界、N2 公共空间降级
- [x] 4.4 archive(notifications spec delta sync)+ commit develop + push

---

> 无 schema 迁移、无高风险操作。所有通知**即时发**(ESCALATED 穿透静音、RESOLVED 即时);quiet_hours 静音延后补发是 **③b**。
