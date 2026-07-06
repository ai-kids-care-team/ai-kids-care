---
globs: backend/**, frontend/**
disclosure: path-scoped
---

# 安全 invariants（不可违背；也用于避免误报）

## 多租户隔离（最高权重约束）

按 `kindergarten_id` 隔离，**靠 ThreadLocal 链**而非 URL 参数。`EffectiveAuthorizationContextFilter` 每请求从 DB 重建 `EffectiveAuthorizationContext`（含 `activeKindergartenId`）存入 ThreadLocal；service 调 `EffectiveAuthorizationContextHolder.requireActiveKindergartenId()` 取值。**前端绝不传 kindergartenId**。租户过滤的 `kindergarten_id` 谓词**必须写进 JPQL/SQL/Cypher**，禁止「加载后过滤」。跨租户/不可见资源**一律 404**（隐藏存在性），不返回 403/200。

## 安全 invariants 1-7

1. **会话式认证（Spring Session + Redis），无 JWT**；不要为前端用户引入无状态 token。授权决策不信任会话快照——每请求重解析、角色/状态撤销下一请求即生效。
2. **CSRF 对所有写请求强制**（`CookieCsrfTokenRepository.withHttpOnlyFalse`，前端回填 `X-XSRF-TOKEN`）；**唯一豁免** = `/api/v1/internal/**`（用 Bearer）。不要把会话端点塞进 internal 前缀或 CSRF 豁免。
3. **default-deny 是设计**：`anyRequest().authenticated()` 兜底，公开白名单极小且按方法精确限定。新端点自动受保护——「端点未配置」是预期，不是 bug。
4. **RRN 单向哈希**（HMAC-SHA256 + pepper，不可逆；列名 `rrn_hash`，历史误名 `rrn_encrypted`）；**摄像头流凭据 AES-256-GCM 可逆 + 版本化**。两种机制**不可混用**。RRN 不落明文/不打日志。
5. **密钥全部 `${ENV}` 注入 + fail-fast**（`@NotBlank`/`@NotEmpty`）；`.env.example` 只放占位，绝不提交真值。secret/PII（RRN、密码、token、session id、raw identifier、请求 body）绝不入日志/审计/异常。
6. **测试占位 / demo ≠ 生产漏洞**：`test-pepper-not-secret-2026`、demo 密码 `admin123` 仅存在于 test/seed；生产 DB 是 Flyway schema-only **无 seed**。报这些为「硬编码 secret / 弱口令」是假阳性。
7. **冷启动管理员** `AdminBootstrapRunner`（env-gated + 空表 + 幂等 + 拒绝 `admin` + 不打密码）与**登录限流** `LoginThrottleService`（Redis 计数 + TTL 锁 → 429，key 用 SHA-256 哈希不存 raw identifier）是受控机制，勿削弱、勿误报。
