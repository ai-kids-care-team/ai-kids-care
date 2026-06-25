# Security Findings (security-analyst, sonnet) — 静态推断，无运行时验证

- SEC-01 [high→lead 升 critical｜backend] 未鉴权 RRN 枚举预言机：`/api/v1/auth/guardian-child-verifications` permitAll 且无限流（LoginThrottleService 只接登录），返回 `{verified}` 布尔。可生日攻击枚举在园儿童 RRN。`AuthService.java:138` / `SecurityConfig.java:97`。修：限流/起始会话令牌。
- SEC-02 [high｜infra] 生产暴露 Swagger/api-docs：permitAll `/swagger-ui/**`、`/v3/api-docs/**`，无 `springdoc.*.enabled=false`，泄露端点/DTO/内部路径。`SecurityConfig.java:72`。修：生产关闭或限 PLATFORM_IT_ADMIN。
- SEC-03 [high｜backend] 摄像头 `sourceUrl`/`playbackUrl` 无协议/主机白名单 → KG_ADMIN 写内网地址，AI 服务抓取 = 两跳 SSRF。`CameraStreamCreateRequest.java:24,35`。修：URL 白名单校验。confidence medium。
- SEC-04 [medium｜backend] SUPERADMIN 可读全平台 AI 模型元数据（仅角色判定，无 scope）。`AuthorizationPolicy.java:26`。修：评估是否需要、收紧为 PLATFORM_IT_ADMIN。
- SEC-05 [medium｜db] seed SQL/CSV 含真实样貌 `rrn_first6` 明文 + 由 test pepper 生成的哈希入库（child C0001）；`db/ne4j_kindergartens/data/*.csv` 把 rrn_first6+rrn_encrypted 明文入库。`26_children_seed.sql` / `500_children.csv`。修：占位值 + CI 阻断 `[0-9]{6},$2a$`。
- SEC-06 [high｜backend] 会话 cookie `Secure` 默认 false，仅 prod overlay 设 true；demo/CD 明文下发会话。`application.yml:53`。修：默认 true。
- SEC-07 [medium｜backend] evidence `uri` 仅 `@NotBlank` 无格式校验 → AI 令牌泄露时可注入 `file://`/内网 URI 存库后被前端/下载器取用（stored-SSRF）。`DetectionEventIngestRequest.java:43`。修：scheme 白名单。
- SEC-08 [low｜backend] Caddy 仅设 HSTS，缺 CSP/X-Frame-Options/X-Content-Type-Options/Referrer-Policy。`infra/caddy/Caddyfile`。
- SEC-09 [low｜backend] 摄像头/班级/教室/公告 CRUD 无审计；`AuditAction` 缺对应枚举。修：补 auditWriter。
- SEC-10 [info｜backend] AI Bearer 用 `MessageDigest.isEqual`，长度不等即短路 → 泄露令牌长度旁路。修：比较 sha256 定长摘要。

Top-3：SEC-01（未鉴权儿童 PII 枚举）、SEC-02（生产 Swagger 暴露攻击面）、SEC-03（摄像头 URL 两跳 SSRF）。
