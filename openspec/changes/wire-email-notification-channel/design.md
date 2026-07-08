## Context

`NotificationChannelEnum` 有 `PUSH`/`SMS`/`EMAIL` 三值，但 `NotificationService.dispatch()` 对 EMAIL 是
`return; // EMAIL delivery not implemented`。PUSH 走 Pushover（`PushPort` + `push_subscriptions`）、SMS
走 Solapi（`SmsPort` + `users.phone`），两者都经 `NotificationDeliveryStore` 的**渠道无关**投递生命周期
（`beginAttempt` at-most-once → provider 调用在事务外 → PRF-01/02 短事务 + 有界超时 → SENT/FAILED）。
`users.email` 列已存在（nullable、unique）。ESCALATED 家长告警当前 = PUSH（总是）+ SMS（有手机号叠加），
本设计把 EMAIL 接成第三条叠加渠道。约束：多租户隔离、密钥 `${ENV}`+fail-fast+不入日志、无 schema 迁移。

## Goals / Non-Goals

**Goals:**
- 让 `channel = EMAIL` 的通知真正投递（SMTP），复用既有渠道无关投递机制。
- ESCALATED 家长通知在 PUSH+SMS 之外叠加 EMAIL（有 `users.email` 才建行），作为独立、best-effort 的书面留痕。
- 端口抽象 `EmailPort` + `SmtpEmailAdapter`，与 `PushPort`/`SmsPort` 同构，provider 可换。
- 凭据姿态与既有 Pushover/Solapi 一致（fail-fast），不破坏无 SMTP 环境的 `compose up`。

**Non-Goals:**
- 教职员告警加 EMAIL（`StaffAlertService` 不动）。
- EMAIL 用于 RESOLVED / 公告 / 感谢信 / 周报摘要。
- HTML 富模板 / Thymeleaf、退信/投诉 webhook、SPF/DKIM 部署配置、多 provider、EMAIL 偏好开关。
- 任何 schema 迁移（复用 `users.email` 与 `notifications` 表）。

## Decisions

**D1 — SMTP via `spring-boot-starter-mail`（`JavaMailSender`）藏在 `EmailPort` 后。**
对比 API SDK（AWS SES / SendGrid）：SDK 有退信遥测但引入耦合与厂商锁定。SMTP 与具体商无关（SES-SMTP /
Gmail / Naver 皆可），且 `EmailPort` 端口让 `NotificationService` 只依赖抽象——与 ARC-01 的 `PushPort`/
`SmsPort` 完全同构，测试 mock 端口而非打真网关。端口方法 `send(String toAddress, String subject, String
body)`；投递失败抛 `IllegalStateException`、参数错抛 `IllegalArgumentException`（与 `SmsPort` 契约一致）。

**D2 — 凭据 fail-fast（随 house style），非 enabled 开关。**
核查现状：`PushoverConfig`/`SolapiConfig` 均 `@ConfigurationProperties` + `@NotBlank`（缺失即启动失败），
靠 `.env.example` 占位值让栈仍能 boot。为一致，EMAIL 用同样姿态：`spring.mail.host/port/username/password`
经 `${ENV}` 注入 + `@NotBlank` fail-fast，`.env.example` 加占位。**否决 enabled 开关**——会引入与 SMS/PUSH
不一致的第三种姿态。占位值（如 `MAIL_HOST=smtp.example.com`）满足 `@NotBlank`，无 SMTP 的 dev/CI 照常启动。

**D3 — 投递侧完全镜像 `dispatchSms`。**
`dispatch()` 的 EMAIL 分支从早退改为路由到新 `dispatchEmail()`：读 `users.email` → 空则 `markFailed` 不
调 provider → `deliveryStore.beginAttempt(notification, "SMTP")` → 端口调用在事务外 → 有界 wall-clock 超时
`notifications.email-send-timeout-ms`（默认 5000，镜像 `sms-send-timeout-ms`；超时记 FAILED 不卡 SENDING）
→ markSucceeded/markFailed。复用 `NotificationDeliveryStore`，零新投递机制。

**D4 — 触发侧：仅 `GuardianNotificationService` 的 ESCALATED 分支叠加。**
ESCALATED 已 eager `loadUsers` 完整 User，`getEmail()` 标量在 detached 下安全（同 SMS 取 `getPhone()` 的
理由）。加第三个 `deliver(...EMAIL..., "evt-{id}-u-{uid}-guardian-email")`，仅当 `email` 非空。教职员告警
不加：`StaffAlertService` 每次 ingest 就发（量大），EMAIL 会成噪音；ESCALATED 家长是低频高价值场景。

**D5 — 内容复用，不引模板引擎。**
告警是短消息：subject = 现有标题 `"안전 알림"`，body = 现有正文，纯文本，韩语后端渲染。V1 不引 Thymeleaf。

**D6 — quiet_hours：ESCALATED 穿透 → EMAIL 即时。** 不引入任何延迟/DEFERRED 逻辑；RESOLVED 保持 PUSH-only。

## Risks / Trade-offs

- **占位 SMTP 在 demo/CI 产生 FAILED 邮件行 + 每收件人至多 5s 超时等待** → 与既有"占位 Solapi 使 SMS 失败"
  行为同构；调用异步（AFTER_COMMIT `@Async`）+ best-effort，超时有界，不阻塞 PUSH/SMS；seed 家长多无 email
  则根本不建行。可接受。
- **EMAIL 作为"紧急"渠道偏弱**（垃圾箱/延迟）→ 定位是**叠加留痕**而非主通道，PUSH/SMS 仍是即时主力。
- **`JavaMailSender` 自动装配** → 依赖 `spring.mail.*` 存在；adapter 注入容器自动装配的 `JavaMailSender`，
  `@NotBlank` 配置类保证凭据非空 fail-fast。
- **投递性 SPF/DKIM/退信** → 明确范围外（部署/运维面），不阻断本 change。

## Migration Plan

纯叠加、**无 schema 迁移**。步骤：① backend 加 `spring-boot-starter-mail` 依赖；② 新增 `EmailPort` +
`SmtpEmailAdapter` + 邮件配置类；③ `application.yml` 加 `spring.mail.*`(`${ENV}`) 与
`notifications.email-send-timeout-ms`；④ `.env.example` 加 SMTP 占位；⑤ `dispatch()` 路由 + `dispatchEmail()`；
⑥ `GuardianNotificationService` ESCALATED 叠加 EMAIL `deliver`。**回滚**：EMAIL 叠加且 best-effort，revert
提交即可，无数据迁移、不影响 PUSH/SMS/RESOLVED。属破坏性（新依赖+配置），apply 前维护者逐项批准。

## Open Questions

- **生产用哪个 SMTP relay**（SES-SMTP / 企业邮 / Naver）——属**部署时**决定（只改 `${ENV}` 值，不改代码），
  不阻断本 change 实现与合入。
- 凭据启用姿态的取舍**已在 D2 定夺**（fail-fast，随 Pushover/Solapi 一致）。
