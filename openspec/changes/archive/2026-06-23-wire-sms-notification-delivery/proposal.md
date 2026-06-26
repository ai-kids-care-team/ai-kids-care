## Why

闭环步骤⑤。④ 已让 AI 端移除 SMS 直发(改走后端 ingest)。后端 SMS 一直是 gap:`NotificationService.dispatch` 对 `channel==SMS` **直接 return**(未实现);`notifications` spec 如实记录「SMS delivery pending rebuild per ADR-0018,address 是 `users.phone`(不是 push_subscriptions)」。而 spec 的「Kindergarten staff immediate alert」要求 staff 应通过 **Pushover 和/或 SMS**(whichever they have;both if both)收到告警 —— 但 ① 的 `StaffAlertService` 当前**只发 PUSH**。

本 change(⑤)在后端重做 SMS delivery:`SmsPort` 端口 + Solapi 适配器,让 `dispatch` 支持 SMS channel(发到 `users.phone`),并让 staff alert 对有手机号的 staff 也发 SMS,兑现 spec 的双通道告警。

## What Changes

- **`SmsPort` 端口 + Solapi 适配器**:`SmsPort` 接口(`send(phone, text)`);`SolapiSmsAdapter implements SmsPort`(Solapi Java SDK,仿 `PushoverService` 用 SDK + catch→`IllegalStateException`);`SolapiConfig`(`solapi.api-key`/`api-secret`/`sender`,`@NotBlank` fail-fast,仿 `PushoverConfig`)。`build.gradle` 加 Solapi Java SDK(`net.nurigo:sdk`)。
- **`NotificationService.dispatch` 支持 SMS**:`channel==SMS` → 取 `recipientUser.phone`(空 → `FAILED`+`fail_reason`)→ `SmsPort.send(phone, body)` → `SENDING`→`SENT`;异常 → `FAILED`。复用现有 `sent_at`/`fail_reason`/`retry_count` 生命周期。`dispatch` 依赖 `SmsPort`(不直依赖 Solapi),便于测试 `@MockBean` + 将来换 provider。
- **`StaffAlertService` 接 SMS**:每个 staff 除现有 PUSH notification 外,若其 `users.phone` 非空则**另建一条 SMS notification**(`dedupe_key='evt-{eventId}-u-{userId}-staff-sms'`,与 PUSH 的 `-staff` 区分)+ dispatch;沿用 best-effort per-recipient try/catch。
- **spec**:`notifications` ADDED「SMS delivery via Solapi adapter」(取代旧「SMS channel — delivery not fully implemented」gap 描述)。

Non-goals:

- **家长 SMS**(③ 决策走 PUSH;家长 SMS 通道另议)。
- 规则引擎 `notification_rules` opt-in;前端看板(⑥);④' evidence。
- **真实 Solapi 发送验证**(无 Solapi 凭据)→ 测试用 `@MockBean SmsPort`;真实发送留集成/部署环境。
- 无 schema 迁移(SMS 用既有 `users.phone` + `notifications.channel=SMS`,无新列)。

## Capabilities

### Modified Capabilities

- `notifications`: ADDED「SMS delivery via Solapi adapter」—— `dispatch` 实现 SMS channel(经 `SmsPort`→Solapi 发到 `users.phone`,SENT/FAILED 生命周期);staff alert 对有手机号 staff 双通道(PUSH+SMS)。取代旧「SMS not implemented」gap。

## Impact

- **产品代码**:新 `SmsPort`/`SolapiSmsAdapter`/`SolapiConfig`;`NotificationService.dispatch`(SMS 分支);`StaffAlertService`(SMS notification)。`build.gradle`(Solapi SDK)。主类 `@EnableConfigurationProperties` 加 `SolapiConfig`。
- **测试**(Testcontainers):`dispatch` SMS → `SmsPort.send` 调用 + SENT(`@MockBean SmsPort`);无 phone → FAILED;staff alert 有 phone staff 得 PUSH+SMS 两条、无 phone 仅 PUSH;`SolapiSmsAdapter` 单元测(mock SDK)。
- **CI**:复用 `backend-java-tests.yml`;**无 schema 迁移、无高风险 schema 操作**(仅加外部 SDK 依赖)。
- **spec**:`notifications` delta。
- **解锁**:staff 双通道告警;SMS 投递能力(将来家长 SMS / 其他场景可复用)。
