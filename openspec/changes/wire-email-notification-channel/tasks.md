## 1. 维护者批准（破坏性/依赖 gate）

- [ ] 1.1 维护者批准新增 `spring-boot-starter-mail` 依赖 + `spring.mail.*` 配置面变更（apply 前逐项批准）
- [ ] 1.2 维护者确认凭据姿态随 house style fail-fast（design D2），`.env.example` 加占位

## 2. 依赖与配置（fail-fast + ${ENV}，绝不入日志）

- [ ] 2.1 `backend/build.gradle` 加 `org.springframework.boot:spring-boot-starter-mail`
- [ ] 2.2 新增邮件配置类（镜像 `PushoverConfig`/`SolapiConfig`）：`@ConfigurationProperties` + `@NotBlank`（host/port/username/password），fail-fast 缺失即启动失败
- [ ] 2.3 `application.yml` 加 `spring.mail.host/port/username/password`（全 `${ENV}` 注入）与 `notifications.email-send-timeout-ms:5000`
- [ ] 2.4 `.env.example` 加 SMTP 占位（`MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/`MAIL_PASSWORD`，占位值满足 `@NotBlank` 使无 SMTP 环境仍能 `compose up`）

## 3. EmailPort 端口 + SMTP adapter（镜像 SmsPort）

- [ ] 3.1 新增 `EmailPort` 接口：`send(String toAddress, String subject, String body)`；投递失败抛 `IllegalStateException`、参数错抛 `IllegalArgumentException`（javadoc 契约与 `SmsPort` 一致，不泄漏 SDK 类型）
- [ ] 3.2 新增 `SmtpEmailAdapter implements EmailPort`：注入容器自动装配的 `JavaMailSender`，发送纯文本邮件；失败翻译为 `IllegalStateException`；不打印地址/凭据/正文敏感内容

## 4. 投递侧：dispatch 路由 + dispatchEmail（镜像 dispatchSms）

- [ ] 4.1 `NotificationService`：注入 `EmailPort`，`dispatch()` 的 EMAIL 分支从 `return; // not implemented` 改为路由到 `dispatchEmail(notification)`
- [ ] 4.2 实现 `dispatchEmail`：读 `users.email`；空/blank → `deliveryStore.markFailed(..., "no email for EMAIL recipient")` 不调 provider；否则 `beginAttempt(notification, "SMTP")`（`alreadyAttempted` 则 reconcile）→ 端口调用在事务外 → markSucceeded/markFailed
- [ ] 4.3 有界超时：镜像 `sendSmsWithinBudget` 实现 `sendEmailWithinBudget`，用 `notifications.email-send-timeout-ms`；超时翻译为 `IllegalStateException` → 记 FAILED，非 non-positive 时禁用包装直调

## 5. 触发侧：ESCALATED 家长叠加 EMAIL（仅 GuardianNotificationService）

- [ ] 5.1 ESCALATED 分支：对每个 `guardianUsers.get(userId)` 的 `getEmail()` 非空者，`deliver(... NotificationChannelEnum.EMAIL, title, body, "evt-{eventId}-u-{userId}-guardian-email", false, null)`（PUSH/SMS 逻辑与 RESOLVED 路径不动）
- [ ] 5.2 确认 EMAIL 仅在 ESCALATED 产生、RESOLVED 不产生；确认 `email` 空时不建 EMAIL 行且不影响该家长 PUSH/SMS

## 6. 测试（TDD）

- [ ] 6.1 `EmailPort` mock 的 `NotificationService` 单测：`channel=EMAIL` 成功→SENT/`sent_at`；空 email→FAILED（不调端口）；端口抛失败→FAILED；超时→FAILED（非 SENDING）
- [ ] 6.2 `GuardianNotificationService` 单测：ESCALATED 且家长有 email → 建 `-guardian-email` EMAIL 行且发到该 email；家长无 email → 不建 EMAIL 行；一家长 EMAIL 失败不影响其 PUSH/SMS 与其他家长；RESOLVED 不建 EMAIL 行
- [ ] 6.3 `SmtpEmailAdapter` 单测：mock `JavaMailSender`，发送失败翻译为 `IllegalStateException`

## 7. 验证与门禁

- [ ] 7.1 `cd backend && ./gradlew compileJava compileTestJava --no-daemon` 通过
- [ ] 7.2 `cd backend && ./gradlew test --no-daemon`（testcontainers）全绿，无既有回归
- [ ] 7.3 `docker compose config` 与无 SMTP 环境 `compose up` 仍可启动（占位凭据满足 fail-fast）
- [ ] 7.4 grep 确认 SMTP 凭据/收件地址不进日志（安全 invariant #5）
- [ ] 7.5 `openspec validate wire-email-notification-channel --strict` 通过
