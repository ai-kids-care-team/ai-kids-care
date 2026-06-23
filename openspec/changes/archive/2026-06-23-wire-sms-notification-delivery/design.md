## Context

闭环步骤⑤,后端(Java)。已核实:

- `NotificationService.dispatch`(`service/NotificationService.java:134`):`channel != PUSH` 直接 return(SMS/EMAIL 未实现);PUSH 路径设 `SENDING` → 查 `push_subscriptions` → `PushoverService.sendToUser` → `SENT`/`markFailed`(SENDING→SENT/FAILED,`sent_at`/`fail_reason`/`retry_count`)。
- `PushoverService` 是适配器模板:构造注入 SDK client + `PushoverConfig`;`sendToUser(userKey,title,message)` → SDK → catch `PushoverException` → `IllegalStateException`(dispatch 只 catch `IllegalStateException` 记 FAILED)。`PushoverConfig` fail-fast(blank token 启动报错)。
- `User.phone`:`@Size(min=10,max=11)`,`uq_user_account_phone` 唯一,nullable。SMS address = `users.phone`(spec)。
- `StaffAlertService`(`service/StaffAlertService.java`):`@Async`,每 staff 建 `Notification(PUSH,QUEUED,dedupe='evt-{e}-u-{u}-staff')` + dispatch,per-recipient try/catch best-effort。
- `build.gradle` **无任何 SMS/Solapi/HTTP-client 依赖**;Pushover 用 `net.pushover.client` SDK。
- spec:`notifications.md` SMS scenario「not implemented」(line 73-76);staff alert「Pushover and/or SMS」(line 225-234);`NotificationChannelEnum` = PUSH/SMS/EMAIL。
- 无 schema 改动(SMS 复用 `users.phone` + `channel=SMS`)。

## Goals / Non-Goals

**Goals:** `dispatch` 实现 SMS channel(SmsPort→Solapi→`users.phone`,SENT/FAILED);staff alert 双通道(PUSH+SMS)。

**Non-Goals:** 家长 SMS;规则引擎;前端;evidence;真实 Solapi 发送验证(测试 mock);schema 迁移。

## Decisions

### D1:端口/适配器分层
`SmsPort` 接口(`void send(String phone, String text)`,失败抛 `IllegalStateException`);`SolapiSmsAdapter implements SmsPort`(`@Service`)。`NotificationService`/`StaffAlertService` 依赖 `SmsPort` 接口,不直依赖 Solapi —— dispatch 测试 `@MockBean SmsPort`,且将来换 provider 只改 adapter。镜像 PushoverService 的注入/异常约定。

### D2:Solapi 集成 = Solapi Java SDK
用官方 `net.nurigo:sdk`(`DefaultMessageService` / `Message` send),仿 PushoverService 用 SDK。`build.gradle` 加依赖。adapter catch SDK 异常 → `IllegalStateException`。**Open Question**:SDK 版本与 send API 形状(apply 第 1 步确认,锁定可解析的稳定版本;若 SDK 集成/依赖解析受阻,退回 HTTP REST + HMAC-SHA256 签名直调 Solapi API)。

### D3:dispatch SMS 分支
`dispatch`(`@Transactional`)对 `channel==SMS`:
1. `phone = notification.getRecipientUser().getPhone()`(lazy 加载,事务内 OK)。
2. `phone` 空 → `markFailed(notification, "no phone for SMS recipient")`,return。
3. 否则 `SENDING` → `smsPort.send(phone, body)` → `SENT`+`sent_at`;catch `IllegalStateException` → `markFailed`。
SMS 用 `body`(SMS 无 title 概念);保持与 PUSH 同一 lifecycle/markFailed。

### D4:staff alert 双通道
`StaffAlertService.alertForEvent`:对每个 staff,除现有 PUSH notification 外,加载其 `User`,若 `phone` 非空则另建一条 `Notification(channel=SMS, dedupe='evt-{e}-u-{u}-staff-sms')` + dispatch。沿用 per-recipient + per-channel try/catch best-effort(一个 staff/通道失败不影响其他)。PUSH 与 SMS dedupe_key 后缀区分,`(kindergarten_id, dedupe_key)` 唯一不冲突。

### D5:SolapiConfig fail-fast
`SolapiConfig`(`@ConfigurationProperties("solapi")`)`api-key`/`api-secret`/`sender` `@NotBlank`,启动 fail-fast(仿 `PushoverConfig`)。主类 `@EnableConfigurationProperties` 加它。`application-test.yml` 给非生产 dev 值(仿 `pushover.api-token`);adapter 在测试由 `@MockBean SmsPort` 取代,不真发。

### D6:测试策略
- `NotificationService` dispatch SMS:`@MockBean SmsPort`,验 SMS notification → `send(phone, body)` 调用 + `SENT`;无 phone → `FAILED`。
- `StaffAlertService`:有 phone staff 得 PUSH+SMS 两条 + 两次 dispatch;无 phone staff 仅 PUSH。
- `SolapiSmsAdapter` 单元:mock SDK,验 send 调用映射 + 异常转 `IllegalStateException`。

## Risks / Trade-offs

- **[Solapi SDK 不确定]** 版本/API/依赖解析/testcontainer 行为未知。隔离:dispatch/staff 测试 `@MockBean SmsPort`(不碰 SDK);adapter 单测 mock SDK;若 SDK 受阻退回 HTTP REST(D2)。
- **[users.phone null]** staff 无手机 → SMS 跳过/FAILED(D3/D4);不阻断 PUSH。
- **[真实发送无法 CI 验证]** 无 Solapi 凭据 → 测试全 mock;真实发送留部署。adapter 真实路径靠人工/部署验证。
- **[staff 双通道放大]** N staff × 2 通道 notification + dispatch;`@Async` 不阻塞 ingest;dedupe 防重。
- **[回滚]** 纯新增 port/adapter/config + dispatch 分支 + staff SMS;git 还原 + 移除 SDK 依赖;无 schema。

## Migration Plan

1. `SmsPort` + `SolapiConfig` + `SolapiSmsAdapter`(`build.gradle` Solapi SDK)+ adapter 单测(mock SDK);主类 `@EnableConfigurationProperties`。
2. **[TDD]** `dispatch` SMS 分支 + 测试(`@MockBean SmsPort`:SMS→SENT、无 phone→FAILED)。
3. **[TDD]** `StaffAlertService` SMS + 测试(有/无 phone)。
4. 容器 `cleanTest` 全套件全绿;`notifications` spec delta;code review(opus);archive + 合 develop + push。
- 回滚:git 还原 + 去 SDK 依赖;无 schema。

## Open Questions

- Solapi Java SDK 版本与 send API(`net.nurigo:sdk` 最新稳定);SDK vs HTTP REST 退路。apply 第 1 步确认。
- SMS 文案/长度(Solapi SMS 90 字节 / LMS 2000 字节限制)——staff alert body 是否超长需截断或用 LMS。apply 第 3 步定。
- `sender` 号码是否需 Solapi 预注册(发信번호 사전등록);记部署 follow-up。
- 家长 SMS(③ 走 PUSH)是否后续也接 SMS:另议。
