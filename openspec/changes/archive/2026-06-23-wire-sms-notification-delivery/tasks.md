## 1. SmsPort + Solapi 适配器 + 配置

- [x] 1.1 `SmsPort` 接口(`void send(String phone, String text)`,失败抛 `IllegalStateException`)
- [x] 1.2 `SolapiConfig`(`@ConfigurationProperties("solapi")`,`api-key`/`api-secret`/`sender` `@NotBlank` fail-fast,仿 `PushoverConfig`);主类 `@EnableConfigurationProperties` 加它;`application-test.yml` 给非生产 dev 值
- [x] 1.3 `build.gradle` 加 Solapi Java SDK(`net.nurigo:sdk` 稳定版;若依赖解析/集成受阻则退回 HTTP REST + HMAC-SHA256 直调 Solapi API)
- [x] 1.4 `SolapiSmsAdapter implements SmsPort`(`@Service`,注入 SDK + `SolapiConfig`;`send` 映射 phone/text → SDK,catch SDK 异常 → `IllegalStateException`,仿 `PushoverService`)
- [x] 1.5 [TDD] `SolapiSmsAdapter` 单元测(mock SDK):send 调用映射 + 异常转 `IllegalStateException`

## 2. NotificationService.dispatch SMS 分支（TDD）

- [x] 2.1 [RED] dispatch SMS 测试(`@MockBean SmsPort`):SMS notification → `send(recipient.phone, body)` 调用 + `SENT`+`sent_at`;recipient 无 phone → `FAILED`+`fail_reason`(不调 send);send 抛 `IllegalStateException` → `FAILED`
- [x] 2.2 `dispatch`:`channel==SMS` → `recipientUser.getPhone()` 空则 `markFailed`;否则 `SENDING` → `smsPort.send(phone, body)` → `SENT`;catch `IllegalStateException` → `markFailed`。复用现有 lifecycle;PUSH 分支不变

## 3. StaffAlertService 双通道（TDD）

- [x] 3.1 [RED] staff alert 测试:有 `phone` 的 staff 得 PUSH + SMS 两条 notification(dedupe `-staff` / `-staff-sms`)+ 两次 dispatch;无 phone staff 仅 PUSH;一通道失败不影响其他/其余 staff
- [x] 3.2 `StaffAlertService.alertForEvent`:每 staff 现有 PUSH 之外,加载 `User`,`phone` 非空则另建 `Notification(channel=SMS, dedupe='evt-{e}-u-{u}-staff-sms')` + dispatch;per-recipient/per-channel try/catch best-effort

## 4. 验证与收尾（verification-before-completion）

- [ ] 4.1 容器内 `gradle:8.7-jdk21` DooD `cleanTest test` 全套件全绿(新增 SMS dispatch/adapter/staff + 既有零回归),留存证据
- [x] 4.2 范围核对(git diff):新增 `SmsPort`/`SolapiSmsAdapter`/`SolapiConfig` + `dispatch` SMS 分支 + `StaffAlertService` SMS + `build.gradle` SDK + 主类/test 配置;**无 schema 迁移**;未碰家长 SMS/规则引擎/前端/evidence
- [x] 4.3 code review(**opus**)
- [x] 4.4 archive(notifications spec delta sync)+ commit develop + push

---

> 无 schema 迁移。真实 Solapi 发送无法 CI 验证 → 测试全 `@MockBean SmsPort` + adapter mock SDK;真实发送留部署。家长 SMS、SMS 文案长度(LMS)、sender 预注册记 follow-up。
