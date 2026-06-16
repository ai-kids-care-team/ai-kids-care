# 安全架构（Security Architecture）

> 本文档**如实描述**当前代码中的安全相关实现与态势，区分已确认事实、推断与待确认项。

> **as-built 更新（2026-06-15，PR #89）**：鉴权机制已由 JWT 无状态改为**服务端会话（Spring Session + Redis）+ 默认拒绝 + 每请求 Effective Authorization Context + 集中 policy + 租户隔离**（[ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md) / [ADR-0009](../decisions/adr/ADR-0009-restore-auth-enforcement.md) / [ADR-0019](../decisions/adr/ADR-0019-effective-authorization-context-tenant-enforcement.md)）。§1–3、§5–6 已据此重写，原 JWT/`permitAll` 描述作废。

✅ 主要来源：`config/SecurityConfig.java`、`security/EffectiveAuthorizationContext{Filter,Service,Holder}.java`、`security/SessionPrincipal.java`、`security/AuthorizationPolicy.java`、`security/SessionRevocationService.java`、`security/AesGcmCryptoUtil.java`、`service/AuthService.java`、`application.yml`、`infra/caddy/Caddyfile`。

## 1. 鉴权组件（已实现的部件）

| 组件 | 实现 |
| --- | --- |
| 密码哈希 | `BCryptPasswordEncoder`（`SecurityConfig`） |
| 会话 | Spring Session + Redis（`store-type=redis`、`repository-type=indexed`）；最小可序列化 `SessionPrincipal`（`user:{id}`）；`SessionCreationPolicy.IF_REQUIRED` |
| 会话 cookie | `AI_KIDS_CARE_SESSION`：`httpOnly`、`SameSite=Lax`、生产 `Secure`（`SESSION_COOKIE_SECURE`）；session id 经 cookie 投递、非 body |
| CSRF | Spring Security cookie CSRF（`XSRF-TOKEN` cookie + `X-XSRF-TOKEN` 头），写请求强制 |
| 鉴权过滤 | `EffectiveAuthorizationContextFilter`（接在 `SecurityContextHolderFilter` 后），每请求权威重解析 |
| 授权 | `@EnableMethodSecurity` + 集中 `AuthorizationPolicy`（service 层 `@PreAuthorize` 读 request-scoped context） |
| 会话吊销 | `SessionRevocationService`（Redis indexed，按 principalName 删全部 session）+ `POST /auth/logout-all` |
| 对称加密 | `AesGcmCryptoUtil`：AES-256-GCM（32B 密钥 / 12B IV / 128b tag），用于摄像头流凭证 |
| CORS | `SecurityConfig` 允许 localhost(:80/:3000)、127.0.0.1、`frontend` 源，允许凭证 |

> JWT 无状态机制（`JwtUtil` / `JwtAuthenticationFilter` / `jjwt` / `STATELESS`）已**移除**（[ADR-0007](../decisions/adr/ADR-0007-jwt-stateless-auth.md) 被 [ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md) 取代）。

## 2. 当前鉴权态势（关键事实）

✅ **默认拒绝**：`SecurityConfig` 将 `/api/v1/**` 设为 `authenticated()`。公开白名单仅限 CSRF bootstrap（`/auth/csrf`）、`/auth/login`、`/auth/register`、`/auth/register/availability`、`/auth/guardian-child-verifications`，以及注册登录前所需的 S3 只读目录/参考（`/kindergartens/**`、`/common_codes/**`、`/menus/**` 的 GET）、`OPTIONS /**`、Swagger/api-docs（生产应关闭）。白名单外匿名访问返回 `401`。

✅ **每请求权威授权**：`EffectiveAuthorizationContextFilter` 对每个已认证请求从数据库权威重解析 ACTIVE user、唯一 ACTIVE role assignment、必要的 ACTIVE membership 与 scope，生成不可由客户端伪造的 request-scoped context。user 禁用 / 角色撤销 / membership 结束使**下一请求** `401` 并失效会话（吊销正确性兜底）。

✅ **集中授权 + 租户隔离**：`@EnableMethodSecurity` + `AuthorizationPolicy` 按 action + role + 数据分类做粗粒度许可；已发布租户资源在查询条件中包含 effective kindergarten / 关系链，跨租户/无关系返回隐藏 `404`（非加载后过滤）。Teacher 资源收紧为有效 assignment 覆盖（classes/rooms）；cameras/streams/sessions 限 `KINDERGARTEN_ADMIN`。平台角色可选已验证 tenant-context，但不改 PLATFORM scope、不获人员 S1 / live CCTV / 录像 / detection evidence 权限。

> 历史：此前 `/api/v1/**` 为 `permitAll` + JWT 过滤器注释的临时演示态（OQ-SEC-1 / [ADR-0009](../decisions/adr/ADR-0009-restore-auth-enforcement.md)）。该态势已于 PR #89（2026-06-15）随会话鉴权 + 默认拒绝落地而**结束**。

## 3. 会话与 CSRF（已实现）

✅ 由 Spring Session + Redis 承担（取代原 JWT）：

- **登录**：`AuthService.login` 校验 ACTIVE user + 密码 + 唯一 ACTIVE role assignment（园级还须唯一匹配 ACTIVE membership）→ `EffectiveAuthorizationContextService.establishSession` 建会话。响应仅返回最小 `AuthSessionVO`（`userId`/`loginId`/`effectiveRole`/`scopeType`/`scopeId`），**不含任何 token/hash/credential**。
- **不信任快照**：role/scope/membership/selected tenant **不**以 Redis 序列化快照作为授权真相，每请求按数据库重解析；session id 属 S0，不进 response/业务日志/审计 detail。
- **会话超时 / CSRF**：`server.servlet.session.timeout`（默认 30m）；CSRF cookie token（`XSRF-TOKEN`）+ 请求头（`X-XSRF-TOKEN`），写请求强制，缺失/无效 `403`。
- **吊销**：logout 删当前 session；`/auth/logout-all` + `SessionRevocationService` 删该 user 全部 session（fast path），每请求重解析为正确性兜底。

> 原 JWT 专属内容（access/refresh 无区分、claim、`expireSecond` 单位、JWT secret 默认值，OQ-SEC-2/3）随机制移除而**作废**。

## 4. 敏感数据保护（已确认）

| 数据 | 处理方式 | 证据 |
| --- | --- | --- |
| 用户密码 | BCrypt 单向哈希 | `AuthService.register` |
| 主民登录号 RRN | 拆分：`rrn_first6`（前6位/出生日期，**明文**，用于检索）+ `rrn_encrypted`（后位，**实为单向哈希；列名为历史命名错误**，见 [ADR-0010](../decisions/adr/ADR-0010-rrn-one-way-hash.md)） | schema 列注释（误导性，见下） |
| RRN 后位的保护 | ✅ `AuthService` 中用 **`passwordEncoder.encode()`（BCrypt 单向哈希，不可逆）** 写入 `rrn_encrypted`——**不可解密** | `registerGuardian`/`registerTeacher` |
| 摄像头流密码 | AES-256-GCM 可逆加密，密文+IV+key_version 分列存储 | `AesGcmCryptoUtil` + `camera_streams` 列 |

> ✅ **已决（2026-05-29，OQ-SEC-4，[ADR-0010](../decisions/adr/ADR-0010-rrn-one-way-hash.md)）**：RRN **采用单向哈希、不可逆**；**不**使用任何形式的可逆加密。
>
> 即：列名 `rrn_encrypted` + schema 注释"암호문(암호화 저장)" + 派生到本知识库的多处"加密"措辞**均为错误/误导性表述**，不反映系统设计意图。代码现状（BCrypt）方向正确。哈希算法的子选项（BCrypt 现状 vs HMAC-SHA-256+pepper）与列改名（`rrn_encrypted` → `rrn_hash`）留待 Implementation，详见 ADR-0010 勘误清单。

## 5. 传输与网络

- ✅ Demo（`docker-compose.yml`）：浏览器→`:80`→frontend nginx（静态 + `/api/` 反代 `backend:8080`），HTTP。
- 🔄 生产（`docker-compose.prod.yml` + `infra/caddy/Caddyfile`，PR #89 草案）：**Caddy 边缘 TLS 终结**（自动 ACME）+ 强制 HTTP→HTTPS + HSTS，反代现有 frontend nginx；Caddy 独占宿主 80/443，frontend 不再发布宿主端口；backend `SESSION_COOKIE_SECURE=true`。**端到端 HTTPS 与真实证书签发待部署时验证**（需公网域名/证书；本地/CI 仅校验 compose 结构可解析）。
- ✅ CORS 显式允许 `allowCredentials=true`，来源限定 localhost/frontend，与"cookie 会话 + `withCredentials`"模式一致。

> `Secure` 会话 cookie 使生产 HTTPS 成为硬前置（[ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md) / [ADR-0017](../decisions/adr/ADR-0017-tls-https-termination.md)，决断 OQ-OPS-3）。

## 6. 凭据管理

- ✅ DB / Neo4j / Redis 凭据通过环境变量注入，且在 `application.yml` 与 `docker-compose.yml` 中带**明文默认值**（`kids_pass`、`rose100!`）。原 JWT secret 已随 JWT 移除。
- ✅ 根 `.env.example` 列出需覆盖的变量（`POSTGRES_*`、`NEO4J_*` 等；`JWT_SECRET` 已废）；生产另需 `DOMAIN`/`ACME_EMAIL`（Caddy）与 `SESSION_COOKIE_SECURE=true`。根 `README` 要求生产用 `.env` 覆盖。
- ✅ 仓库存在 `.env` 文件（89 字节）——其内容未纳入本知识库；请确认其是否含真实密钥及是否应被 git 忽略（见 OQ-SEC-5）。

## 7. 审计

✅ **安全审计 writer 已实现**（SPEC-0001 #1，审计要求 §270-298；schema 见 [ADR-0021](../decisions/adr/ADR-0021-admin-audit-schema-migration.md) 的 V2 迁移）：

- **correlation id**：`CorrelationIdFilter`（`HIGHEST_PRECEDENCE`，包裹整条安全链）为每个请求分配随机 correlation id（或净化后的入站 `X-Correlation-Id` 头，≤64 安全字符），写入 MDC + 响应头 `X-Correlation-Id`。**不使用 session id**（session id 属 S0）。
- **写入器**：`SecurityAuditWriter` **直写 `audit_logs`**（不复用被关闭的 `AuditLogService` CRUD 栈），独立事务（`REQUIRES_NEW`，使拒绝/失败在业务回滚路径上仍持久），best-effort（写入失败只记日志、不阻断业务）。只落结构化字段（actor id / scope / action / result / resource type+id / ip / user-agent / correlation id），**绝不写**密码 / RRN / token / session id / 请求 body（§282/§296）。`action`/`result` 由应用层枚举 `AuditAction`/`AuditResult` 约束（DB 维持 varchar）。
- **接入点**：登录成功 / 失败、登出、`/auth/logout-all` 会话吊销、平台 tenant context 切换（`scope_type=PLATFORM` 且 `kindergarten_id=NULL`，不伪造 kg id——§284）、园级 / 平台审批 approve/reject/disable（角色变更）、授权拒绝（`@PreAuthorize` 403 经 `SecurityAuditAccessDeniedHandler`；审批内细粒度 403 / 跨租户隐藏 404 经 service try/catch）。S1 / evidence 读取预留 `AuditAction.S1_EVIDENCE_READ`，当前对应控制器为空壳、无活动调用点。
- **append-only**：应用层保证（writer 只 insert，无 update/delete）；`AuditLogController` 不发布公共写 / 删 operation。DB 级 `REVOKE UPDATE/DELETE` / 触发器属高风险权限，**维护者另行决定，不在本次**（ADR-0021 §4）。
- ⏳ 待办（OQ）：拒绝洪泛限流 / 采样（每个 403 / CSRF 失败写一行，存在审计洪泛向量）；可信 `X-Forwarded-For` 客户端 IP 解析（待边缘反代定案，[ADR-0017](../decisions/adr/ADR-0017-tls-https-termination.md)，现记 `remoteAddr`）；DB 级 append-only 强约束。

---

## 安全相关待确认项索引

详见 [modernization/open-questions.md](../modernization/open-questions.md)：OQ-SEC-1（鉴权关闭，✅ 已实现 PR #89 → [ADR-0009](../decisions/adr/ADR-0009-restore-auth-enforcement.md) / [ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md)）、OQ-SEC-2/3（JWT access/refresh / secret，✅ 随 JWT 移除消解）、OQ-SEC-4（RRN 哈希策略 / 命名勘误，已决 → [ADR-0010](../decisions/adr/ADR-0010-rrn-one-way-hash.md)）、OQ-SEC-5（`.env` 与默认凭据）、OQ-SEC-6（DEBUG 日志）、OQ-SEC-7（审计落地，✅ 安全事件 writer 已实现，见 §7；残留拒绝洪泛限流 / 可信 forwarded IP / DB 级 append-only 为后续 OQ）。
