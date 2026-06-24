## 1. 家长手机号解析

- [x] 1.1 在 `GuardianNotificationService` 解析出家长 `user_id` 集合后,批量取这些 user 的 `phone`(`userRepository.findAllById(...)` → `Map<userId, phone>`),用于判断哪些家长有非空手机号。

## 2. SMS 通道(TDD)

- [x] 2.1 先写失败测试:`ESCALATED` 复核 + 某家长 user 有非空 `users.phone` → 该家长**同时**产生 `PUSH`(`-guardian`)与 `SMS`(`-guardian-sms`)两条 `notifications`;SMS 经 mock `SmsPort` 发到该 phone。先看红。
- [x] 2.2 在 `ESCALATED` 路径:对「phone 非空」的家长**额外**建一条 `channel = SMS`、`dedupeKey = "evt-"+eventId+"-u-"+userId+"-guardian-sms"`、`recipientUser = 同一家长 user` 的通知并 `dispatch`。PUSH 建法/触发矩阵保持不变。看 2.1 转绿。
- [x] 2.3 补测试:`ESCALATED` 但家长 user `phone` 为 null/blank → 只建 PUSH、不建 SMS。
- [x] 2.4 补测试:`RESOLVED`(opted-in)→ 只建 PUSH、不建 SMS(SMS 仅 `ESCALATED`)。
- [x] 2.5 补测试:某家长 SMS 发送失败(mock `SmsPort` 抛错)→ 该家长 SMS `FAILED`,其 PUSH 与其他家长通知不受影响(per-recipient/per-channel best-effort)。
- [x] 2.6 确认 dedupe:同事件重复确认 → `-guardian` / `-guardian-sms` 各自被 `UNIQUE(kindergarten_id, dedupe_key)` 挡重复。

## 3. 验证

- [x] 3.1 后端 DooD 全套件回归(`gradle:8.7-jdk21`,`cleanTest test`,host override/ryuk-disabled/挂 repo 根),0 fail;测试全程 mock `SmsPort`,不打真实 Solapi。（由 Lead 在整合时运行一次)
- [x] 3.2 自查:无 schema 迁移;PUSH 路径行为零变化(既有 PUSH 测试仍绿)。
