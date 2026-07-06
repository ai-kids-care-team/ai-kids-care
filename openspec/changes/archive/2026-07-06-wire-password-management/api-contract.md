# API 契约 — wire-password-management (UX-07)

> **冻结**。前后端唯一真源。字段名/可空性/状态码逐字段对齐。前端**绝不传 kindergartenId**。
> 所有 4 个端点均为**写请求 → 受 CSRF**(前端回填 `X-XSRF-TOKEN`,先 `GET /api/v1/auth/csrf`)。错误响应 shape 统一 `{ "error": string }`(沿用 `AuthController` 既有 `@ExceptionHandler`)。

---

### change-password — 已登录用户自助改密

- **路径**:`POST /api/v1/auth/change-password`
- **方法**:POST
- **鉴权**:会话(Spring Session + Redis)+ CSRF `X-XSRF-TOKEN`。**不在公开白名单**(default-deny 自动保护)。
- **授权**:任意已认证用户改**自己**的密码;当前用户取自 `EffectiveAuthorizationContextHolder.require().userId()`,**不信任请求体身份**。

#### 请求
- **DTO 类名**:`ChangePasswordRequest`(复用既有孤儿 DTO)

| 字段 | 类型 | 可空 | 校验/说明 |
|------|------|------|-----------|
| currentPassword | string | 否 | `@NotBlank`;当前密码明文 |
| newPassword | string | 否 | `@NotBlank` + `@ValidPassword`(minLength/字母/数字/非全同) |

#### 响应
- **成功**:`204 No Content`,**无 body**。
- **副作用**:改密成功后**吊销该用户全部会话(含当前会话)**;前端须清认证态并跳登录页,用新密码重新登录。

#### 错误契约
| 情况 | 状态码 | body |
|---|---|---|
| currentPassword 不匹配 | 400 | `{ "error": "현재 비밀번호가 올바르지 않습니다." }` |
| newPassword 不满足复杂度 / 空 | 400 | `{ "error": "<@ValidPassword 韩文消息>" }` |
| 未认证 | 401 | `{ "error": "..." }` |
| 缺 CSRF | 403 | (Spring 默认) |

#### 前端对齐点
- `frontend/src/services/apis/auth.api.ts` → 新增 `changePassword` mutation(RTK Query `baseApi`,`invalidatesTags: ['AuthSession']`)。
- 挂 `frontend/src/components/settings/SettingsModal.tsx` 改密卡。成功回调:清 `user` reducer + 跳登录。

---

### password-reset/request — 未登录发起重置(防枚举,恒 200)

- **路径**:`POST /api/v1/auth/password-reset/request`
- **方法**:POST
- **鉴权**:**公开**(`SecurityConfig` POST 白名单新增)+ CSRF `X-XSRF-TOKEN`(仍强制)。
- **授权**:无(匿名可达)。

#### 请求
- **DTO 类名**:`PasswordResetRequestDTO`(新建)

| 字段 | 类型 | 可空 | 校验/说明 |
|------|------|------|-----------|
| loginId | string | 否 | `@NotBlank`;登录 ID(仅按 loginId 查,不接受 email/phone,减少枚举面) |

#### 响应
- **VO 类名**:`VerificationCodeCreateVO`(复用既有孤儿 VO)
- **状态码**:**恒 `200`**(账号存在与否、有无 phone 均返回同结构)

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| challengeId | string | 否 | 不透明随机 UUID(即使账号不存在也返回一个哑 challengeId) |
| expiresAt | string(ISO-8601 OffsetDateTime) | 否 | challenge 过期时刻(如 now+5min) |

- **副作用**:仅当账号存在且 `users.phone` 非空时,经 `SmsPort.send` 发 6 位验证码 SMS(文案不含 PII);否则静默(写哑 challenge)。

#### 错误契约
| 情况 | 状态码 | body |
|---|---|---|
| loginId 空 | 400 | `{ "error": "..." }` |
| 限流触发(按 SHA-256(loginId)计数) | 429 | `{ "error": "..." }`(generic,存在/不存在账号同样受限,不泄存在性) |

> **防枚举硬约束**:账号存在 vs 不存在 → 响应体结构、字段、状态码逐字节同构。绝不因存在性返回不同响应。

#### 前端对齐点
- `auth.api.ts` → `passwordResetRequest` mutation。响应读 `challengeId`/`expiresAt`。
- `frontend/src/components/auth/ForgotPasswordForm.tsx`(第一步:输入 loginId)。

---

### password-reset/verify — 校验短信码换取 resetToken

- **路径**:`POST /api/v1/auth/password-reset/verify`
- **方法**:POST
- **鉴权**:**公开**(POST 白名单)+ CSRF。
- **授权**:无。

#### 请求
- **DTO 类名**:`PasswordResetVerifyDTO`(新建)

| 字段 | 类型 | 可空 | 校验/说明 |
|------|------|------|-----------|
| challengeId | string | 否 | `@NotBlank`;来自 request 响应 |
| code | string | 否 | `@NotBlank`;用户从 SMS 输入的 6 位码 |

#### 响应
- **VO 类名**:`PasswordResetVerifyVO`(新建;字段名统一为 `resetToken`,勿用孤儿 VO 的 `verificationToken`)
- **状态码**:`200`

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| resetToken | string | 否 | 短寿命**单次性** token(256-bit 随机);仅用于 confirm |
| expiresAt | string(ISO-8601 OffsetDateTime) | 否 | resetToken 过期时刻 |

#### 错误契约
| 情况 | 状态码 | body |
|---|---|---|
| 码错 / challenge 过期或不存在 / 哑 challenge / 尝试超 5 次锁定 | **统一 400** | `{ "error": "인증에 실패했습니다." }`(统一消息,不区分具体原因,防 oracle) |

#### 前端对齐点
- `auth.api.ts` → `passwordResetVerify` mutation。成功读 `resetToken`,导航到 `/reset-password` 携带 resetToken(路由 state/query,用后即弃,不持久化)。
- `ForgotPasswordForm.tsx`(第二步:输入短信码)。

---

### password-reset/confirm — 用 resetToken 落新密码

- **路径**:`POST /api/v1/auth/password-reset/confirm`
- **方法**:POST
- **鉴权**:**公开**(POST 白名单)+ CSRF。
- **授权**:无(凭 resetToken)。

#### 请求
- **DTO 类名**:`PasswordResetConfirmDTO`(新建)

| 字段 | 类型 | 可空 | 校验/说明 |
|------|------|------|-----------|
| resetToken | string | 否 | `@NotBlank`;来自 verify |
| newPassword | string | 否 | `@NotBlank` + `@ValidPassword` |

#### 响应
- **成功**:`200`,**无 body**。
- **副作用**:落新 bcrypt;`delete` resetToken(单次);吊销该 userId 全部会话。

#### 错误契约
| 情况 | 状态码 | body |
|---|---|---|
| resetToken 无效/过期/已用 | 400 | `{ "error": "..." }` |
| newPassword 不满足复杂度 | 400 | `{ "error": "<@ValidPassword 韩文消息>" }` |

#### 前端对齐点
- `auth.api.ts` → `passwordResetConfirm` mutation。成功后跳登录页提示用新密码登录。
- `frontend/src/components/auth/ResetPasswordForm.tsx`(输入新密码 + 确认)。

---

## 前端其它对齐点(非端点)

- `frontend/src/components/auth/LoginForm.tsx:136-138` — 现为静态文案 "비밀번호 재설정은 아직 제공되지 않습니다.",改为指向 `/forgot-password` 的可点 `<Link>`。
- 双 HTTP 客户端(`baseApi` RTK Query / `apiClient` Axios)拦截器均已注入 CSRF;沿用现有模式,不新造 CSRF 逻辑。

## enum / 分页
- **无线上 enum**:`PurposeEnum.PASSWORD_RESET` 仅后端 Redis 内部标记,不出现在任一端点线协议 → 无三处同步负担。
- **无分页**。

## 错误 shape 汇总
所有端点错误 body 统一 `{ "error": string }`(`AuthController` 既有 `@ExceptionHandler(ResponseStatusException)` 与 `@ExceptionHandler(MethodArgumentNotValidException)`)。前端一律读 `error` 字段。
