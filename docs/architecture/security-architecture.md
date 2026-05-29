# 安全架构（Security Architecture）

> 本文档**如实描述**当前代码中的安全相关实现与态势，区分已确认事实、推断与待确认项。按知识库约定与 Discovery 模式，本文档**不提供修复方案或建议**——风险仅作客观记录，处置决策留给团队（相关项汇总于 [open-questions](../modernization/open-questions.md)）。

✅ 主要来源：`backend/.../config/SecurityConfig.java`、`security/JwtUtil.java`、`security/JwtAuthenticationFilter.java`、`security/AesGcmCryptoUtil.java`、`service/AuthService.java`、`application.yml`。

## 1. 鉴权组件（已实现的部件）

| 组件 | 实现 |
| --- | --- |
| 密码哈希 | `BCryptPasswordEncoder`（`SecurityConfig`） |
| JWT | `JwtUtil`：HMAC-SHA 签名（`Keys.hmacShaKeyFor`），`subject`=登录标识符，`jjwt 0.12.3` |
| JWT 过滤器 | `JwtAuthenticationFilter`：解析 `Authorization: Bearer`，校验后置入 `SecurityContext` |
| 对称加密 | `AesGcmCryptoUtil`：AES-256-GCM（32B 密钥 / 12B IV / 128b tag），用于摄像头流凭证 |
| CORS | `SecurityConfig` 允许 localhost(:80/:3000)、127.0.0.1、`frontend` 源，允许凭证 |
| 会话 | `SessionCreationPolicy.STATELESS`（无服务端会话） |

## 2. 当前鉴权态势（关键事实）

> ❓ **重要：API 当前未受鉴权保护。**

✅ 直接证据（`SecurityConfig.java`）：

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/swagger-ui/**", ...).permitAll()
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .requestMatchers("/api/v1/**").permitAll()      // ← 所有业务 API 放行
        .anyRequest().authenticated()
);
//      .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);  // ← JWT 过滤器被注释停用
```

两点叠加的客观后果：

1. `/api/v1/**`（即全部业务接口）对**任何无凭证请求开放**。
2. JWT 过滤器**未加入**过滤链，即便带 token 也不会被解析/强制。

✅ 该状态与前端实现**不一致**：前端 `apiClient.ts` 完整实现了 Bearer 注入、401 刷新、强制登录，且注释记录"部分端点对无认证调用返回 401"。这强烈暗示**鉴权曾经是开启的**，当前为开发/演示而临时放开（🔶 推断），而非设计终态。

> 处置与意图判定见 [open-questions](../modernization/open-questions.md)（OQ-SEC-1）。

## 3. JWT 设计细节（已确认）

✅ `JwtUtil` + `AuthService`：

- 令牌仅含 `subject`（登录标识符）、`issuedAt`、`expiration`，**不含角色/权限 claim**。
- ✅ `AuthService.login()` 中，**accessToken 与 refreshToken 由同一方法 `generateToken(identifier)` 生成**，二者除签发瞬间外**无差异**（无 token 类型 claim、无独立过期时间）。
- ✅ 过期时间来自 `jwt.expiration: 86400000`（24h）。注意：`AuthService` 把该值读为 `Integer expireSecond` 并作为 `expiresIn` 返回给前端，但 `JwtUtil` 把它当**毫秒**用于 `expiration`。字段名 `expireSecond` 与单位（毫秒）不符。
- ✅ JWT secret 有硬编码默认值 `mySecretKeyForJWTTokenGenerationThatIsAtLeast256BitsLong`（`application.yml` / compose fallback）。

> ❓ 见 OQ-SEC-2（access/refresh 无区分）、OQ-SEC-3（secret 默认值与单位混淆）。

## 4. 敏感数据保护（已确认）

| 数据 | 处理方式 | 证据 |
| --- | --- | --- |
| 用户密码 | BCrypt 单向哈希 | `AuthService.register` |
| 主民登录号 RRN | 拆分：`rrn_first6`（前6位/出生日期，**明文**，用于检索）+ `rrn_encrypted`（后位） | schema 列注释 |
| RRN 后位的"加密" | ✅ `AuthService` 中实际用 **`passwordEncoder.encode()`（BCrypt 单向哈希）** 写入 `rrn_encrypted` | `registerGuardian`/`registerTeacher` |
| 摄像头流密码 | AES-256-GCM 可逆加密，密文+IV+key_version 分列存储 | `AesGcmCryptoUtil` + `camera_streams` 列 |

> ❓ **不一致点**（OQ-SEC-4）：schema 注释称 RRN 为"암호문(암호화 저장)"（密文/加密存储，暗示可逆），但代码用 BCrypt（**不可逆**）。同时仓库提供了可逆的 `AesGcmCryptoUtil`。RRN 究竟应**可逆加密**（以便展示/核验）还是**单向哈希**（仅校验），当前实现与注释/工具能力矛盾，需确认权威方案。

## 5. 传输与网络

- ✅ 生产经 Nginx：浏览器→`:80`→`/api/`反代→`backend:8080`（HTTP）。仓库内**未见 TLS/HTTPS 配置**（🔶 推断由外层基础设施终结 TLS，或仅用于内网/演示）。
- ✅ CORS 显式允许 `allowCredentials=true` 且来源限定 localhost/frontend，与"前端 JS 携带 token"模式一致。

## 6. 凭据管理

- ✅ DB / Neo4j / JWT 密钥均通过环境变量注入，且在 `application.yml` 与 `docker-compose.yml` 中带**明文默认值**（`kids_pass`、`rose100!`、上述 JWT secret）。
- ✅ 根 `.env.example` 列出需覆盖的变量（`POSTGRES_*`、`NEO4J_*`、`JWT_SECRET`），根 `README` 明确要求生产环境用 `.env` 覆盖。
- ✅ 仓库存在 `.env` 文件（89 字节）——其内容未纳入本知识库；请确认其是否含真实密钥及是否应被 git 忽略（见 OQ-SEC-5）。

## 7. 审计

- ✅ 存在 `audit_logs` 表（action/resource_type/resource_id/ip/user_agent）与 `AuditLogController`/`AuditLogService`。
- ❓ 各写操作是否实际产生审计记录，需核对各 service 是否调用审计写入（未见统一切面/拦截器）。

---

## 安全相关待确认项索引

详见 [modernization/open-questions.md](../modernization/open-questions.md)：OQ-SEC-1（鉴权关闭）、OQ-SEC-2（access/refresh 无别）、OQ-SEC-3（JWT secret 默认值/单位）、OQ-SEC-4（RRN 加密 vs 哈希）、OQ-SEC-5（`.env` 与默认凭据）、OQ-SEC-6（DEBUG 日志）、OQ-SEC-7（审计落地）。
