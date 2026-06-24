## Context

现状(只读核查,含 file:line):
- `GuardianNotificationService.java`:`@Async @TransactionalEventListener(AFTER_COMMIT)` `onEventReviewed`(`:59`)→ `notifyOnReview`。触发矩阵(`:73-77`):`ESCALATED` 必发、`RESOLVED` 仅 `notifyGuardians=true` 发、其余不发。家长解析(`:126-146`)= room→active class→active children→active guardian→`user_id`(关系图,PostgreSQL)。每个家长建 `Notification`:`channel = PUSH` 硬编码(`:104`)、`dedupeKey = "evt-"+eventId+"-u-"+userId+"-guardian"`(`:113`)、`recipientUser = userRepository.getReferenceById(userId)`(`:107`);quiet-hours 决定 `DEFERRED` 或立即 `dispatch`(`:111-117`)。
- `NotificationService.dispatch`(`:135`):`channel == SMS` → `dispatchSms`(`:188`),从 `notification.getRecipientUser().getPhone()`(即 `users.phone`)取号;空号 → `FAILED`;否则 `SENDING` → `smsPort.send(phone, body)` → `SENT/sent_at`,失败 `FAILED`。channel enum = `PUSH/SMS/EMAIL`。
- guardians 表**无 phone 列**;家长手机号在 `users.phone`(`guardians.user_id` 1:1 → `users`)。`dispatchSms` 读的 `recipientUser.getPhone()` 正是家长 user 的 phone——**通道已通**,只差「建一条 SMS 通知」。
- quiet-hours:`GuardianNotificationService` 只对 `RESOLVED` 算 quiet window;`ESCALATED` 恒立即(`:96-98`)。`DeferredNotificationScanner` channel-agnostic(`dispatch` 内按 channel 路由)。
- dedupe:staff 用 `-staff` / `-staff-sms` 两个 key 让 PUSH/SMS 共存于 `UNIQUE(kindergarten_id, dedupe_key)`(`StaffAlertService.java:79/84`)。家长当前只有 `-guardian`(PUSH)。

## Goals / Non-Goals

**Goals:**
- `ESCALATED` 时,有手机号的家长**同时**收到 PUSH 与 SMS,确保最严重事件即使无 Pushover 订阅也能触达。
- PUSH 路径与现有触发矩阵**零行为变化**。
- 零 schema;复用步骤⑤ SMS 基础设施与 `users.phone`。
- per-recipient + per-channel best-effort。

**Non-Goals:**
- `RESOLVED` 发 SMS(本期仅 `ESCALATED`)。
- 家长 SMS 的 quiet-hours 延后(本期 SMS 仅随 `ESCALATED`,而 `ESCALATED` 恒立即,故无 deferred-SMS 路径)。
- 家长偏好/退订渠道选择(per-guardian channel preference)。
- EMAIL 通道。

## Decisions

**D1 — SMS 仅加在 `ESCALATED`(双通道 PUSH+SMS);`RESOLVED` 维持仅 PUSH。**
- 理由:SMS 打扰且有按条成本。`ESCALATED`=人工复核判定为需升级的严重事件,值得双通道强触达;`RESOLVED`=信息性,PUSH 足够。与 `StaffAlertService`「对有手机号 staff 发 PUSH+SMS」的既有取舍一致。
- 备选:(a) `ESCALATED`+`RESOLVED` 都发 SMS——过度打扰/成本高,弃;(b) 仅当家长**无** PUSH 订阅时回退发 SMS——省成本但需先查订阅、且"回退"语义复杂、与 staff 双通道不一致,弃;选双通道-on-ESCALATED。

**D2 — SMS 通知仅对「`users.phone` 非空」的家长创建(与 staff 一致),而非无条件创建再让 `dispatchSms` 标 FAILED。**
- 理由:避免给无号家长刷一堆 `FAILED` SMS 行(噪声);与 spec「staff … over SMS (when the recipient has a `users.phone`)」一致。
- 实现:对解析出的家长 `user_id` 集合,批量 `userRepository.findAllById(...)` 取 `phone`,只对 phone 非空者建 SMS 通知。PUSH 仍对所有家长建(行为不变)。

**D3 — dedupe key 后缀 `-guardian-sms`,与 `-guardian`(PUSH)区分,共存于 `UNIQUE(kindergarten_id, dedupe_key)`。** 镜像 staff `-staff`/`-staff-sms`。重复确认同事件→同 key→唯一约束挡重复。

**D4 — quiet-hours:本期 SMS 仅随 `ESCALATED`,恒立即,不进 DEFERRED。** 若未来给 `RESOLVED` 也加 SMS,可直接复用已 channel-agnostic 的 `dispatch`/scanner 延后逻辑(那时再扩 spec)。

**D5 — per-recipient + per-channel best-effort。** 沿用现有 per-recipient `try/catch`(`:119-122`):某家长 SMS 建/发失败,不影响该家长 PUSH、也不影响其他家长。通知失败不回滚复核事务(AFTER_COMMIT 既有保证)。

## Risks / Trade-offs

- **[成本/频次:`ESCALATED` 对每个有号家长发 SMS]** → 缓解:仅 `ESCALATED`(低频、严重);dedupe 防同事件重复确认重发。运维成本在 proposal Impact 标注。
- **[家长 user 普遍无 phone → SMS 覆盖有限]** → 本期 best-effort:有号才发、无号仅 PUSH。提高家长号采集率是产品运营事项,非本 change。
- **[Solapi 真实发送 / 발신번호 预注册]** → 与步骤⑤同:测试全程 mock `SmsPort`,不打真实 Solapi;真实发号留部署 follow-up。

## Migration Plan

无 schema 迁移。回滚 = 还原 `GuardianNotificationService` 改动;PUSH 路径不受影响。

## Open Questions

- 是否将来给 `RESOLVED` 也加 SMS(则需接 quiet-hours 延后)?本期 Non-Goal。
- 家长是否需要 per-guardian 渠道偏好/退订?独立 follow-up。
