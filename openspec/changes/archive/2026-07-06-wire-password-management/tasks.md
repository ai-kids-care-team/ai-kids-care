# Tasks — wire-password-management (UX-07)

契约冻结于 `api-contract.md`。后端 lane 与前端 lane 各对着契约并行,TDD。

## 后端 lane (backend/)

### 1. 已登录改密
- [ ] 1.1 写测试(先看失败):已认证用户 POST 正确 currentPassword + 合规 newPassword → 204;旧密码写为新 bcrypt(matches);currentPassword 错 → 400;newPassword 不合规 → 400;改密后该用户既有会话失效(下一请求 401 / revokeAllForUser 被调用)。
- [ ] 1.2 实现 `POST /api/v1/auth/change-password`:controller 分发 → service `@Transactional` 校验旧密(`passwordEncoder.matches`)、存新 bcrypt、`SessionRevocationService.revokeAllForUser(userId)`;user 取自 `EffectiveAuthorizationContextHolder`。接线孤儿 `ChangePasswordRequest`。返回 204。
- [ ] 1.3 确认 `@ValidPassword` 在边界拦复杂度,错误走 `{error}` handler。

### 2. 忘记密码 — Redis 验证码/token store + 限流
- [ ] 2.1 写测试:store 写 challenge(codeHash/userId/attempts)、TTL、verify 计数与锁、resetToken 单次(用后删)。复用 `StringRedisTemplate`。
- [ ] 2.2 实现 Redis store(如 `PasswordResetTokenService`):challengeId=UUID、code=6 位、SHA-256(code)、resetToken=256-bit SecureRandom;键 `pwreset:challenge:{id}` / `pwreset:token:{token}`;TTL ~5min。code/token/phone 绝不入日志。
- [ ] 2.3 实现限流(复用 `LoginThrottleService` 模式):request 按 SHA-256(loginId)、verify 按 challengeId attempts≤5;Redis 故障 best-effort 放行。

### 3. 忘记密码 — 三端点(防枚举)
- [ ] 3.1 写测试(防枚举为核心):request 对存在/不存在/无 phone 账号 → 响应逐字节同构、恒 200 + { challengeId, expiresAt };仅存在且有 phone 时 `SmsPort.send` 被调用(mock 断言参数不含 PII 泄漏)。
- [ ] 3.2 写测试:verify 正确码 → 200 + { resetToken, expiresAt };哑 challenge/错码/过期/超 5 次 → 统一 400;resetToken 重放 → 400。confirm 有效 token + 合规新密 → 200 + 新 bcrypt + revokeAllForUser;无效 token → 400;新密不合规 → 400。
- [ ] 3.3 实现 `POST /api/v1/auth/password-reset/{request,verify,confirm}`:新建 `PasswordResetRequestDTO`/`PasswordResetVerifyDTO`/`PasswordResetConfirmDTO` + `PasswordResetVerifyVO`(resetToken/expiresAt);复用 `VerificationCodeCreateVO`;SMS 发送在事务边界外、失败不改响应(防枚举)、仅 warn(无 PII)。
- [ ] 3.4 `SecurityConfig`(约 :94-98)POST 白名单追加三条 reset 端点;确认 change-password **不**进白名单;三端点 CSRF 仍强制。
- [ ] 3.5 全套件绿:`cd backend && ./gradlew test`(DooD:挂 repo 根 + `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`)。

## 前端 lane (frontend/)

### 4. API 接线
- [ ] 4.1 `services/apis/auth.api.ts` 增 4 个:`changePassword`(authed)、`passwordResetRequest`、`passwordResetVerify`、`passwordResetConfirm`(公开但 CSRF)。类型逐字段对齐契约(challengeId/expiresAt/resetToken)。导出 hooks。
- [ ] 4.2 确认公开 reset 调用前已 `GET /api/v1/auth/csrf`(拦截器回填 X-XSRF-TOKEN);绝不传 kindergartenId。

### 5. 改密 UI
- [ ] 5.1 `SettingsModal.tsx` 加改密卡(currentPassword + newPassword + 确认;前端复杂度提示与后端 `@ValidPassword` 对齐)。成功:清 `user` reducer + 跳登录页提示用新密码登录。错误读 `{error}`。

### 6. 忘记密码 UI(点亮死占位)
- [ ] 6.1 `ForgotPasswordForm.tsx`:两步(输入 loginId → request;输入短信码 → verify),verify 成功导航 `/reset-password` 携带 resetToken(路由 state,用后即弃)。恒 200 后统一提示"若账号存在,验证码已发送"(不泄存在性)。
- [ ] 6.2 `ResetPasswordForm.tsx`:输入新密 + 确认 → confirm;成功跳登录。无 resetToken(直接进页)则引导回 `/forgot-password`。
- [ ] 6.3 `LoginForm.tsx:136-138` 静态文案改为指向 `/forgot-password` 的可点 `<Link>`。
- [ ] 6.4 绿:`cd frontend && npm run lint && npm run build`(node 原生优先,回退 node:20 容器;提交前还原 next-env.d.ts)。

## 门禁(dev-lead 收口)
- [ ] G1 ①硬测试门:后端 test 绿 + 前端 lint&build 绿。
- [ ] G2 ②`/code-review` 合并 diff。
- [ ] G3 ③`security-analyst`(防枚举/限流/CSRF/密钥不落日志/授权)+ `integration-analyst`(契约逐字段吻合)。
- [ ] G4 ④findings 清零或如实标注。
