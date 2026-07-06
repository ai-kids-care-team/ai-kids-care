# Design — wire-password-management (UX-07)

## 上下文与约束

- **零 schema**:所有临时状态(验证码、resetToken、限流计数)落 **Redis TTL**,复用 Spring Session 已有的 `StringRedisTemplate` 基建,不新增依赖、不建表。
- **会话式认证**:改密端点已鉴权(default-deny 自动保护);三个 reset 端点未登录可达,但**仍受 CSRF**(唯一 CSRF 豁免是 `/api/v1/internal/**`)。
- **密码复杂度**:新密一律走既有 `@ValidPassword` → `PasswordConstraintValidator`(minLength/requireLetter/requireDigit/rejectAllSame)。
- **secret/PII 不入日志**:验证码明文、resetToken、`users.phone`、RRN 绝不进日志/审计/异常消息。

## 决策 1:已登录改密

`POST /api/v1/auth/change-password`(会话 + CSRF)。

- 当前用户从 `EffectiveAuthorizationContextHolder.require().userId()` 取(**不信任请求体里的任何身份**)。
- 校验旧密:`passwordEncoder.matches(currentPassword, user.getPasswordHash())`;不匹配 → `400 { error }`(会话有效,属 bad request,不用 401)。
- 存新密:`user.setPasswordHash(passwordEncoder.encode(newPassword))`,`@Transactional` 保存。
- **改后吊销全会话**:调 `SessionRevocationService.revokeAllForUser(userId)`(该方法删该 principal 的全部 indexed session,**含当前会话**)。返回 `204`,前端清认证态并跳登录页,用新口令重新登录。选择"含当前会话一并吊销"是因 `revokeAllForUser` 本就删全部,且改密即"疑似凭据轮换",全设备重登最安全,实现最简。
- 复杂度校验失败由 `@Valid` + `@ValidPassword` 在 controller 边界拦下 → `400 { error: <韩文校验消息> }`(走 AuthController 既有 `MethodArgumentNotValidException` handler,shape `{error}`)。

## 决策 2:忘记密码三步流(防枚举是硬约束)

### 时序不可区分原则
无论账号是否存在、`phone` 是否为空,`request` 恒返回 `200 + { challengeId, expiresAt }`,challengeId 恒为不透明随机 UUID。差异只在**是否真发 SMS**(账号存在且 phone 非空才发),对客户端不可观测。

### Redis 存储模型(键 + TTL)
| 键 | 值(JSON 或 hash) | TTL | 说明 |
|---|---|---|---|
| `pwreset:challenge:{challengeId}` | `{ codeHash: SHA-256(code) hex, userId: Long\|null, attempts: int }` | ~5 min | request 时写;非存在账号/无 phone 写**随机 codeHash + userId=null**(哑 challenge,保证 verify 一致失败) |
| `pwreset:token:{resetToken}` | `userId`(纯值) | ~5–10 min | verify 成功时写,**单次**:confirm 用后立即 `delete` |
| `pwreset:throttle:*`(request/verify 各一前缀) | 计数 | 窗口 TTL + 锁 TTL | 复用 `LoginThrottleService` 模式,key=SHA-256(loginId 或 challengeId),不存 raw |

- `challengeId` = `UUID.randomUUID()`(不透明);`code` = 6 位数字(仅经 SMS 送真实用户,后端只存 SHA-256);`resetToken` = 256-bit URL-safe 随机(`SecureRandom`)。
- **verify**:按 challengeId 取 challenge;`attempts++`;`SHA-256(code) == codeHash && userId != null` → 成功,签发 resetToken(写 `pwreset:token`),删 challenge;否则 `400`(统一错误码)。`attempts >= 5` → 删/锁 challenge(后续一律 400)。哑 challenge(userId=null)因 codeHash 随机,attacker 无从命中 → 与"账号存在但码错"同为 400,不泄存在性。
- **confirm**:按 resetToken 取 userId;取不到(过期/已用/伪造) → `400`;取到 → 存新 bcrypt(`@ValidPassword` 已在边界校验),`delete` resetToken(单次),`SessionRevocationService.revokeAllForUser(userId)`(该用户可能在别处登录),返回 `200`。

### SMS 发送
账号存在且 `phone` 非空时,构造验证码文案(**不含任何 PII**,仅"[AI Kids Care] 인증번호: NNNNNN (5분)")经 `smsPort.send(user.getPhone(), text)` 发送。发送**在事务边界外**(SMS provider IO 不占 DB 连接),失败不改变对客户端的响应(仍 200,防枚举);失败仅 warn 级日志(不含 phone/code)。

### 限流(复用 LoginThrottleService 模式)
- `request`:按 SHA-256(loginId) 计数,超阈值(如 5/窗口)→ 锁,锁中恒 `429`(generic,不泄存在性 —— 存在/不存在账号同样被限)。
- `verify`:按 challengeId 的 `attempts` 上限(5)+ 可选 IP/challengeId 限流。
- Redis 故障 best-effort 放行(可用性优先),同 `LoginThrottleService`。

## 决策 3:DTO/VO 接线(尽量复用孤儿,按需新建)

| 端点 | 请求 | 响应 |
|---|---|---|
| change-password | 复用 `dto/ChangePasswordRequest`(currentPassword+newPassword,newPassword 已带 `@ValidPassword`) | 204 无 body |
| reset/request | 新建 `PasswordResetRequestDTO { loginId }`(孤儿 `VerificationCodeCreateRequest` 是 channel/to/purpose,与 loginId 流不符,不复用) | 复用 `vo/VerificationCodeCreateVO { challengeId, expiresAt }` |
| reset/verify | 新建 `PasswordResetVerifyDTO { challengeId, code }`(孤儿 `VerifyVerificationCodeRequest` 只有 code,缺 challengeId) | 新建 `PasswordResetVerifyVO { resetToken, expiresAt }`(孤儿 VO 字段名是 verificationToken,契约统一为 `resetToken`) |
| reset/confirm | 新建 `PasswordResetConfirmDTO { resetToken, newPassword @ValidPassword }`(孤儿 `ResetPasswordRequest` 只有 newPassword,缺 resetToken) | 200 无 body |

> enum `PurposeEnum.PASSWORD_RESET` 仅内部使用(Redis purpose 标记),**不出现在任一端点线协议**,故无 DB/后端/前端三处 enum 同步负担。

## 决策 4:SecurityConfig 白名单

在既有 POST `permitAll` 块(约 :94-98,含 login/register/guardian-child-verifications)追加三条:
```
POST /api/v1/auth/password-reset/request
POST /api/v1/auth/password-reset/verify
POST /api/v1/auth/password-reset/confirm
```
`change-password` **不进白名单**(已鉴权,`.requestMatchers("/api/v1/**").authenticated()` 自动保护)。三 reset 端点公开但 CSRF 仍强制(非 internal 前缀)。

## 决策 5:前端流程

- **改密**:`SettingsModal` 加改密卡(currentPassword + newPassword + 确认),调 `changePassword`;成功后因全会话被吊销 → 清 `user` reducer + 跳登录页提示"请用新密码登录"。
- **忘记密码**:`ForgotPasswordForm` 承载 request→verify 两步(输入 loginId → 收到 challengeId → 输入短信码 → 拿 resetToken),verify 成功后导航到 `/reset-password` 并携带 resetToken;`ResetPasswordForm` 承载 confirm(输入新密 → 成功跳登录)。resetToken 页间传递用路由 state/query(静态导出无 SSR,注意不落持久存储、用后即弃)。
- **CSRF**:三 reset 端点公开但受 CSRF,前端在提交前确保已 `GET /api/v1/auth/csrf`(拦截器回填 `X-XSRF-TOKEN`)。双客户端(RTK Query baseApi / Axios apiClient)拦截器均已注入 CSRF,沿用现有模式。
- **绝不传 kindergartenId**;错误展示读响应 `{ error }`。

## 防枚举验收要点(测试必须覆盖)
1. `request` 对存在账号 vs 不存在账号 vs 存在但无 phone → 三者响应体结构与状态码**逐字节同构**(challengeId 均为随机 UUID)。
2. `verify` 对哑 challenge(不存在账号)与真 challenge 错码 → 同为 `400` 同 shape。
3. 验证码、resetToken、phone、RRN **不出现在任何日志/响应/审计**。
4. verify 尝试超 5 次 → 锁,后续 `400`。
5. resetToken 单次:confirm 成功后重放同 token → `400`。
6. 改密/重置成功后该用户既有会话失效(下一请求 401)。
