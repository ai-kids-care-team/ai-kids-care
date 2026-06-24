## Why

复核确认 `ESCALATED` 时,后端只给家长发 `PUSH`(Pushover)。但家长若没有 active 的 Pushover 订阅,这条通知直接 `FAILED`——**家长根本收不到**最严重事件的告警。SMS 基础设施(`SmsPort`/`SolapiSmsAdapter`,步骤⑤)和家长手机号(`users.phone`,经 `guardians.user`)**都已就绪**:`NotificationService.dispatchSms` 已经读 `recipientUser.getPhone()`,`GuardianNotificationService` 已把 `recipientUser` 设成家长的 user。缺的只是「给家长也建一条 SMS 通知」。

## What Changes

- `GuardianNotificationService`:对 `ESCALATED` 复核,在既有 `PUSH` 之外,**额外**给每个「其 `users.phone` 非空」的家长建一条 `channel = SMS` 通知,`dedupe_key = 'evt-{eventId}-u-{guardianUserId}-guardian-sms'`,经既有 `NotificationService.dispatch` 走 SMS 分支(→ `users.phone`)。
- `RESOLVED`(即便 opted-in)**仍只发 PUSH**——SMS 较打扰且有成本,本期只用于最严重的 `ESCALATED`(与 `StaffAlertService` staff 双通道告警一致的取舍)。
- PUSH 路径行为**完全不变**;SMS 是独立增量通道,per-recipient + per-channel best-effort(某家长 SMS 失败不影响其 PUSH 或他人)。
- 无 schema:channel enum 已含 `SMS`;`dispatchSms` 已解析 `users.phone`;quiet-hours scanner 已 channel-agnostic。

## Capabilities

### New Capabilities
<!-- 无新增 capability。 -->

### Modified Capabilities
- `notifications`: 修改「Guardian notification on review confirmation」——在家长 review-gate 通知里增加 SMS 通道:`ESCALATED` 对有手机号的家长**同时**发 PUSH 与 SMS(各自独立 dedupe key);`RESOLVED` 维持仅 PUSH;无手机号家长仅 PUSH。

## Impact

- `backend/src/main/java/com/ai_kids_care/v1/service/GuardianNotificationService.java`(`ESCALATED` 路径增建 SMS 通知,按家长 `users.phone` 是否非空决定是否建)。
- 复用 `NotificationService.dispatchSms` / `SmsPort` / `SolapiSmsAdapter`(步骤⑤)、`users.phone`、`notifications.channel = SMS`、`UNIQUE(kindergarten_id, dedupe_key)`。
- 测试:`GuardianNotificationService` / e2e 增加 SMS 通道断言(mock `SmsPort`,不打真实 Solapi)。
- 非破坏:无 schema、无 API 变化;PUSH 行为不变。运维注意:`ESCALATED` 会对每个有手机号的家长发 SMS(成本/频次),由 dedupe 防重复确认重发。
