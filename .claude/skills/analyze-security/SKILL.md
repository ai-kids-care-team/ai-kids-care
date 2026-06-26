---
name: analyze-security
description: 从安全角度审查组件——认证、授权与多租户隔离、PII/RRN 与密钥处理、注入/CSRF/SSRF、内部接口鉴权、审计。security-analyst 使用。当需要安全审查、漏洞评估、租户隔离/越权检查、敏感数据/密钥审计时使用。
---

# analyze-security — 安全角度审查方法

以**威胁视角**审查：默认每条防线都可能被绕过，去找绕过路径。按 finding schema 输出（id 前缀 `SEC-`），高危项必带 location + evidence + 影响/复现。

## 为何这样审（原则）
本工程处理儿童 PII（RRN 居民登记号）、摄像头流、多租户数据——一次越权或泄露的后果是合规与信任灾难。所以**宁可多报存疑项（标 confidence）也不漏关键路径**。

## 检查清单（按本工程栈）

### 认证
- Spring Session + Redis：cookie 是否 httpOnly、生产 Secure、SameSite 合理。
- CSRF：写请求是否强制校验 `X-XSRF-TOKEN`；白名单端点是否最小。
- 会话失效 / 角色撤销：是否下一次请求即生效（不靠 TTL 等待）。
- AdminBootstrapRunner：冷启动管理员供给是否仅在受控条件、不在常规路径暴露。

### 授权 / 多租户隔离（最高权重）
- 所有写操作是否有 `@PreAuthorize`；有无漏网的 controller 方法。
- **查询是否在 JPQL/Cypher 层带 `kindergarten_id`**，而非加载后再过滤（后者仍泄露存在性）。
- 跨租户资源 id 是否返回 **404 而非 403**（避免泄露存在）。
- 角色范围：KG_ADMIN 仅本园、PLATFORM_IT_ADMIN 跨园是否正确分界。

### 敏感数据
- RRN：是否仅存 HMAC-SHA256 + pepper 哈希（注意是 HMAC 非 AES）；pepper 是否环境化、生产非 `test-pepper-not-secret-2026`；`rrn_first6` 展示是否最小化。
- 摄像头流密码：AES-256 密钥是否环境化、版本化可轮换（camera_stream_aes_key_v1）。
- **Neo4j PII 投影**：图节点是否误带 rrn_*/phone/email/birth_date/address/contact_*/password_hash（scrub 脚本是否真生效）。

### 密钥 / 注入 / 内部接口
- 硬编码 secret；fail-fast 校验（@NotBlank）是否覆盖关键 env。
- SQL/Cypher 字符串拼接；AI `/predict/upload` 上传校验（类型/大小/路径穿越）；SSRF（摄像头流 URL、ai→backend）。
- `POST /api/v1/internal/detection-events`：Bearer 令牌强度、`ROLE_AI_SERVICE` 是否最小权限、令牌泄露面。

### 审计
- 关键操作（CREATE/UPDATE/DELETE、登录、角色变更）是否落 `audit_logs`（actor/resource/action）。

## 手法
- Grep：`@PreAuthorize`、`@Query`、`@Value`、`password`、`secret`、`rrn`、`AES`、`HMAC`、`@JsonProperty`、Cypher `MERGE/CREATE`。
- 区分演示与生产：seed 演示账户、测试 pepper 是预期，确认它们不进生产路径即可，不误报。
- 无法动态验证 → 标「静态推断」并降 confidence。

## 协作
边界处鉴权缺口 → SendMessage `integration-analyst` 求证调用面；结构性根因 → `architecture-analyst`。完成写 `_workspace/security_findings.md` 并通知 lead（附 critical/high 清单）。
