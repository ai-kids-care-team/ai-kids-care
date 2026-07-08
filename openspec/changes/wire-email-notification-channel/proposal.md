## Why

`NotificationChannelEnum` 早已定义 `EMAIL`，但 `NotificationService.dispatch()` 对它是 `return; // EMAIL delivery not implemented` —— 一个已注册却从未接通的渠道。安全平台里，ESCALATED（复核确认为真）的家长告警是最高价值通知；今天它走 PUSH（总是）+ SMS（有手机号）双渠道，但缺一条**有书面留痕、独立于 App 推送/运营商短信**的备份路径。家长若未装 App、未开推送、或短信被拦，仍应收到一封确认邮件。本变更把 EMAIL 作为 ESCALATED 家长告警的**第三条叠加渠道**真正接通。

## What Changes

- **EMAIL 成为 ESCALATED 家长通知的叠加渠道**：在现有 PUSH（总是）+ SMS（有 `users.phone`）之外，再叠加 EMAIL（有 `users.email`）。RESOLVED 保持 PUSH-only 不变；教职员告警（`StaffAlertService`）不变。
- **接通投递侧**：`NotificationService.dispatch()` 的 EMAIL 分支从"未实现直接返回"改为路由到新的 `dispatchEmail()`，完全镜像 `dispatchSms` 的投递生命周期（at-most-once `beginAttempt` → provider 调用在事务外 → 有界超时 → SENT/FAILED）。
- **新增 `EmailPort` 端口抽象 + `SmtpEmailAdapter`**（SMTP，基于 `spring-boot-starter-mail` 的 `JavaMailSender`），镜像既有 `PushPort`/`SmsPort` 端口模式（ARC-01）——`NotificationService` 只依赖端口，换 provider 只换 adapter。
- **BREAKING（依赖 + 配置）**：新增 `spring-boot-starter-mail` 依赖；新增 `spring.mail.*` 与 `notifications.email-send-timeout-ms` 配置，SMTP host/port/账密全部 `${ENV}` 注入 + fail-fast（镜像 `PushoverConfig`/`SolapiConfig` 的 `@NotBlank` 姿态），`.env.example` 增占位。apply 前须维护者批准。

## Capabilities

### New Capabilities
<!-- 无新增能力：EMAIL 是既有 notifications 能力下的一条渠道。 -->

### Modified Capabilities
- `notifications`: 家长通知渠道从「PUSH + SMS（ESCALATED）」扩展为「PUSH + SMS + EMAIL（ESCALATED）」；EMAIL 收件地址取 `users.email`、空地址不投递而记 FAILED、穿透 quiet_hours 即时发送、幂等键独立。

## Impact

- **代码（backend）**：`NotificationService`（`dispatch` 路由 + 新 `dispatchEmail`）、`GuardianNotificationService`（ESCALATED 分支叠加 EMAIL `deliver`）、新增 `EmailPort` + `SmtpEmailAdapter` + 邮件配置类。
- **依赖**：backend 新增 `spring-boot-starter-mail`。
- **配置 / 部署**：`application.yml` 新增 `spring.mail.*`（`${ENV}`）与 `notifications.email-send-timeout-ms`（默认 5000）；`.env.example` 新增 SMTP 占位；无 schema 迁移（复用既有 `users.email` 与 `notifications` 表）。
- **不影响**：SSE 线协议、AI→backend ingest 契约、多租户隔离、PUSH/SMS 现有行为、RESOLVED 路径、教职员告警。
