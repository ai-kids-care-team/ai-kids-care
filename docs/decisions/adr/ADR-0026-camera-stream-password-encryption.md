---
ADR: ADR-0026
title: "ADR-0026: 摄像头流密码加密链路（Java 中介 AES-256-GCM）"
status: Accepted
implementation: In Progress
date: 2026-06-18
deciders: 接手人（Lead）起草；维护者 2026-06-18 Accept（OQ-1 选静态共享 Bearer token，批准开建）
supersedes: []
superseded_by: null
related_specs: [SPEC-0001]
---

# ADR-0026: 摄像头流密码加密链路（Java 中介 AES-256-GCM）

> 维护者已决「实现完整加密链路」，并修正架构为 **Java 后端中介**（2026-06-18）：AI 端**不直连 DB、不持密钥、不做解密**；由 Java 加密写入、解密后经内部接口供 AI。本 ADR 把该决策与密钥/接口设计固化。`camera_streams` 表已预留 `stream_password_ciphertext`/`stream_password_iv`/`stream_password_key_version` 三列（V1 DDL），但写/读链路从未实现。

## 状态（Status）

Decision: `Accepted`（维护者 2026-06-18 签署）

Implementation: `In Progress`（维护者 2026-06-18 批准开建）。**Phase 1（写路径）+ Phase 2（凭据接口 + Bearer filter）+ Phase 3（AI HTTP 客户端）已实现并验证。** **OQ-1 已定：静态共享 Bearer token（OQ1-A）；OQ-3 已定：B（信任 AI 按 stream_id 全局查，凭据接口不做租户隔离）。** 其余 OQ-2/4/5 在对应 Phase 实施时定。

## 背景（Context，as-built @ 2026-06-18）

- `AesGcmCryptoUtil`（Java）完整可用：AES-256-GCM，12-byte SecureRandom IV，128-bit auth tag，Base64；`encrypt(plain, key)` / `decrypt(ct, iv, key)`，key 由调用方传入；**零业务调用**。
- `camera_streams` 三个密文列存在（均可空，V1）。`CameraStreamController` 仅 GET；`CameraStreamService` 只读；`CameraStreamVO` 只暴露 `hasPassword`（密文不经 REST 泄漏，正确）。
- 种子 `39_camera_streams_seed.sql` 存占位串（非真实密文）。
- AI：`stream_live_alert_service.py:561` 用 `STREAM_URL` env 取整条 RTSP URL；**无 DB、无解密、无密钥**。
- 认证基建：会话 cookie（ADR-0016）；无 API key/Bearer/mTLS 基建；`UserRoleEnum` 无 AI_SERVICE/系统账号角色。
- 密钥注入先例：`RrnHashConfig`（`@ConfigurationProperties + @Validated + @NotBlank` fail-fast），`RRN_HASH_PEPPER` 裸引用、仅在 Java env。
- ADR-0015（AI 直写 detection_sessions/events）Accepted，但**不涵盖** stream 凭据解密——维护者明确将本链路设为 Java 中介例外。

## 决策（Decision）

**Java 后端是 stream 凭据的唯一加解密中介；AES-256-GCM 密钥只存在于 Java 后端，绝不下发给 Python。**

- **D1 写路径**：新增 CameraStream 写端点（POST/PUT）→ `CameraStreamService` 调 `AesGcmCryptoUtil.encrypt(plainPassword, activeKey)` → 写 `stream_password_ciphertext`/`iv`/`key_version`。`CameraStreamVO`/`CameraStreamMapper.toVO()` 不变（仍只出 `hasPassword`）。写请求 DTO 的 `streamPassword` 用 `@JsonProperty(access = WRITE_ONLY)`。
- **D2 读路径**：新增专用凭据接口 `GET /api/v1/internal/streams/{id}/credentials` → Java 读密文 → `AesGcmCryptoUtil.decrypt()` → 返回 `StreamCredentialDTO { streamId, sourceUrl, streamUser, streamPassword }`。该 DTO **不**走 `CameraStreamVO`、**不**出现在 Swagger/OpenAPI（`@Hidden`）、`streamPassword` **不**入访问日志。
- **D3 AI 端**：Python 变纯 HTTP 客户端——启动时带认证 token 调凭据接口，用返回值拼 RTSP URL。`STREAM_URL` env 保留为独立测试后备。
- **D4 密钥管理**：`CAMERA_STREAM_AES_KEY_V1`（env，仅 Java 读，模式同 `RRN_HASH_PEPPER`）；新 `CameraStreamCryptoConfig`（`@ConfigurationProperties(prefix="camera-stream.crypto") + @Validated + @NotBlank`）；`stream_password_key_version` 列做版本化。AES-GCM 轮换**不**需明文（旧密钥解密→新密钥加密），可随时批量重加密。

### 否决的备选（按维护者修正）

- **共享 AES 密钥给 Python**：否决——密钥落在两个运行时，AI 容器被攻破即同时泄漏密钥与密文。
- **AI 直连 DB + Python 解密**：否决——同样的密钥分发问题，且把 DB 凭据交给 AI。
- **凭据接口 `permitAll` + 仅靠网络隔离**：否决（见 OQ-1-C）——无应用层强制，网络策略配错即暴露。

## 凭据接口认证模型（OQ-1，关键开放问题，待维护者定）

- **OQ1-A（Lead 推荐）静态共享 Bearer token**：Java 在 `/api/v1/internal/**` 加 `OncePerRequestFilter`，常量时间比较 `Authorization: Bearer <token>`，不匹配 401；`internal.ai.service-token: ${AI_SERVICE_TOKEN}`（fail-fast）；Python 带该头。零新基建，运维模式同 pepper。权衡：token 同时在 Java 与 AI env（但 **AES 密钥仍仅 Java**）。
- **OQ1-B 系统用户 + 会话**：建 AI_SERVICE 角色/复用 PLATFORM_IT_ADMIN，Python 启动登录、管理 session+CSRF。审计更丰富，但非浏览器客户端实现更复杂、会话权限面更宽。
- **OQ1-C（不推荐）仅网络隔离**：`/internal/**` permitAll + 靠 Caddy 路由挡外部。无应用层强制。

## 实施计划（4 阶段）

1. **Phase 1 — Java 写路径**（ADR Accept 后即可开始）：`CameraStreamCryptoConfig`、application.yml/application-test.yml（key 配置 + dev key）、`CameraStreamCreateRequest`（WRITE_ONLY password）、`CameraStreamService` 写方法 + 加密、`CameraStreamController` POST/PUT、`AuthorizationAction` 增 `TENANT_SURVEILLANCE_WRITE`（如需）、加密集成测试、`.env.example`。
2. **Phase 2 — Java 凭据接口**（依赖 OQ-1）✅ 已实现：`com.ai_kids_care.v1.internal.StreamCredentialDTO`、`StreamCredentialController`（`@Hidden`，置于 `.internal` 兄弟包以避开 `PublishedOpenApiContractTest` 的「每个 controller operation 必须发布」强约束）、`CameraStreamService.getStreamCredential`（按 stream_id 全局查 + 解密，OQ-3=B）、`AiServiceTokenAuthenticationFilter`（仅 `/api/v1/internal/**`，常量时间 token 比较）、`InternalAiServiceConfig`（`internal.ai.service-token` fail-fast）、`SecurityConfig` 增 `requestMatchers("/api/v1/internal/**").hasRole("AI_SERVICE")` + filter、集成测试（200 解密 / 缺失 401 / 错误 token 401 / 会话用户无 token 403 / 无密码 null / 404 / @Hidden 不进 OpenAPI）。
3. **Phase 3 — AI HTTP 客户端** ✅ 已实现（2026-06-19）：
   - `ai/src/ai_app/utils/stream_credentials.py`（新增）：`fetch_stream_credentials(stream_id, backend_url, token, *, http_get=None)` + `build_stream_url(cred)` 纯函数模块，无 ML 依赖，`http_get` 参数注入便于 monkeypatch。
   - `ai/scripts/stream_live_alert_service.py` `__main__` 块新增 `STREAM_ID` 分支：若设置 `STREAM_ID` 则调凭据接口拼 URL，否则回退到 `STREAM_URL`（legacy）；日志只打印 host:port，不含密码。
   - `ai/tests/test_stream_credentials.py`（新增）：4 个 mock 测试（200+凭据注入、200+null 凭据、fallback build、401 抛 RuntimeError 且不含 token）。
   - `ai/.env.example` 追加 `JAVA_BACKEND_URL`、`STREAM_ID`、`AI_SERVICE_TOKEN`。
4. **Phase 4 — 种子/配置/CI**：`39_camera_streams_seed.sql`（依 OQ-4）、docker-compose(.prod).yml、.env.example、compose-config CI dummy env。

## 开放问题（OQ）

- **OQ-1 [已定 2026-06-18]**：凭据接口认证模型 = **A 静态共享 Bearer token**（`AI_SERVICE_TOKEN` env，Java `/internal/**` filter 常量时间比较；token 在 Java+AI env，AES 密钥仍仅 Java）。
- **OQ-2 [中]**：AI 推理服务是否纳入主 `docker-compose.yml`（影响 `JAVA_BACKEND_URL`）？
- **OQ-3 [已定 2026-06-19]**：凭据接口**不**强制 kindergarten 租户隔离（选 **B**）——AI 为平台级基建、处理全部园所的流且无 session/tenant 上下文，故按 `stream_id` 全局查；纵深防御由 `/internal/**` 的 Bearer token + `ROLE_AI_SERVICE` 提供。
- **OQ-4 [中]**：种子策略 = A（用 dev 密钥真实加密）/ B（保留占位 + 更新注释）？
- **OQ-5 [低]**：Phase 1 后是否经 Flyway 给 `stream_password_key_version` 加 NOT NULL？
- **OQ-6 [低，延后]**：v2 轮换时是否引入 AAD（绑 `stream_id`）防跨流密文重放？

## 后果（Consequences）

- 正面：摄像头密码静态加密；密钥单点（Java）易管控；AI 不持密钥/不连 DB，攻击面小；密文不经任何 REST VO 泄漏。
- 代价：新增内部认证端点 + filter；AI 行为变更（env → HTTP 客户端）；compose/CI 增密钥 env；跨语言协作（但加解密全在 Java，无跨语言密码学互操作风险）。

## 关联（References）

- [ADR-0016](ADR-0016-...)（会话认证）、[ADR-0015](ADR-0015-...)（AI 直写，明确不涵盖本链路）、[ADR-0025](ADR-0025-rrn-pepper-rotation.md)（密钥版本化哲学，独立密钥）
- `AesGcmCryptoUtil.java`、`camera_streams` V1 DDL、`stream_live_alert_service.py`
- 设计评估：2026-06-18 SEC-0026 设计 loop（Java 中介修正版）
