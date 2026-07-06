## Why

UX-07:平台目前**完全没有密码管理能力**。① 已登录用户无法自助修改密码 —— `SettingsModal` 里预留了改密卡位(第 18 行注释),但没有任何后端端点;孤儿 DTO `ChangePasswordRequest`(currentPassword+newPassword)是生成后从未接线的死代码。② 忘记密码的用户无路可走 —— `app/(auth)/forgot-password`、`app/(auth)/reset-password` 两个页面与 `ForgotPasswordForm`/`ResetPasswordForm` 都是写死"기능은 아직 제공되지 않습니다"的死占位;`LoginForm.tsx:136-138` 也只是静态文案。这在一个长期生产系统里是硬缺口:口令一旦泄露/遗忘,用户无自助补救,只能找运维手工改库。

目标:**两块都做且零 schema 迁移** —— (1) 已登录改密;(2) 未登录自助忘记密码(唯一现实通道 = SMS,复用既有 `SolapiSmsAdapter`/`SmsPort`;验证码存 Redis TTL,禁止建表)。忘记密码流程以**防用户枚举**为硬约束:是否存在的账号必须返回逐字节一致的响应。

## What Changes

- **已登录改密(新)**:新增 `POST /api/v1/auth/change-password`(会话已鉴权,default-deny 自动保护,仍受 CSRF)。校验旧密(`passwordEncoder.matches`)→ 存新 bcrypt(新密走既有 `@ValidPassword` 复杂度校验)。**改密成功后吊销该用户全部会话**(复用 `SessionRevocationService.revokeAllForUser`,类 logout-all),强制所有设备用新口令重新登录。前端接线到 `SettingsModal` 的改密卡。
- **自助忘记密码(新,三步,防枚举)**:
  - `POST /api/v1/auth/password-reset/request { loginId }` → **恒返回 200 + { challengeId, expiresAt }**;仅当账号存在且 `users.phone` 非空时才真发 SMS 验证码,否则静默(存哑 challenge 保持时序一致)。
  - `POST /api/v1/auth/password-reset/verify { challengeId, code }` → 成功返回**短寿命单次性 resetToken**;错误/过期/超次统一 400。
  - `POST /api/v1/auth/password-reset/confirm { resetToken, newPassword }` → 200,存新 bcrypt(走 `@ValidPassword`),吊销该用户全部会话。
  - 三个端点按方法精确加入 `SecurityConfig` POST 白名单(未登录可达),但**仍受 CSRF**(前端先 `GET /api/v1/auth/csrf` 拿 token 再回填)。
- **验证码/限流基建(新,零迁移)**:验证码与 resetToken 全部存 **Redis TTL**(以 challengeId/token 为键,存 SHA-256(code)、purpose、目标 userId、尝试计数);复用 `LoginThrottleService` 模式(Redis 计数 + TTL 锁,key 用 SHA-256 不存 raw identifier)对 request/verify 限流。
- **前端点亮占位**:`ForgotPasswordForm`/`ResetPasswordForm` 两死占位实现为真流程;`LoginForm.tsx:136-138` 静态文案改为指向 `/forgot-password` 的可点链接;`SettingsModal` 加改密卡。

## Non-goals(非目标)

- **不做 email 通道**(email 发送未实现);**不做 push 通道**(未登录拿不到订阅,鸡生蛋)。忘记密码唯一通道 = SMS。
- **不建任何新表 / 不做 schema 迁移**:验证码、resetToken、限流计数全部落 Redis TTL(禁止 `verification_codes`/`reset_token` 表)。
- **不做管理员代重置 / 后台强制改密**(`must_change_password` 列)。
- **不做多实例分布式一致性**(Redis TTL 单实例假设,同 `LoginThrottleService`;多实例去重已无限期搁置)。
- **不改密码复杂度策略**:直接复用既有 `@ValidPassword`/`PasswordConstraintValidator`。
- **不真发 Solapi**(CI 用 stub;真实发送属部署面)。

## Capabilities

### Modified Capabilities
- `auth-authorization`:新增已登录改密(校验旧密 + 新密复杂度 + 改后吊销全会话)与未登录自助密码重置(防枚举三步 SMS 流 + Redis TTL 验证码/单次 resetToken + 限流);三个 reset 端点精确进公开白名单但仍受 CSRF。

## Impact

- 后端:新增 `AuthController` 4 个端点 + `AuthService`(或新 `PasswordManagementService`)改密/重置逻辑 + Redis 验证码/token store + 复用 `SmsPort`/`SessionRevocationService`/`LoginThrottleService` 模式 + `SecurityConfig` 白名单增 3 条 POST。接线孤儿 DTO/VO。**无实体/schema 变更**。
- 前端:`auth.api.ts` 增 4 个调用;`ForgotPasswordForm`/`ResetPasswordForm` 实现;`SettingsModal` 加改密卡;`LoginForm` 链接。
- 安全红线:三 reset 端点公开但受 CSRF;验证码/token/phone/RRN 绝不入日志;防枚举恒定响应;限流防爆破。
