---
ADR: ADR-0017
title: "ADR-0017: TLS/HTTPS 终结与强制"
status: Accepted
implementation: In Progress
date: 2026-06-07
deciders: 接手人起草，维护者 Accept（2026-06-07）；实现委派独立 session
---

# ADR-0017: TLS/HTTPS 终结与强制

> **前瞻提案**。由 [ADR-0016](ADR-0016-server-side-session-auth.md)（服务端会话）**硬性触发**：`Secure` 会话 cookie 在 HTTP 下不会被发送 → 生产 HTTPS 从"可选加固"升级为"会话鉴权能否工作的硬前置"。

## 状态（Status）

Accepted（2026-06-07）。**生产部署前置**，与 [ADR-0012](ADR-0012-production-data-lifecycle.md)（生产部署）、[ADR-0016](ADR-0016-server-side-session-auth.md)（Secure cookie）耦合。实现委派独立 session。

> 实现状态（2026-06-15；已随 [PR #89](https://github.com/ai-kids-care-team/ai-kids-care/pull/89) 合入 `develop`，merge commit `36cfdd4`）：决策第 1 条"二选一属实现"的组件选型**定为 Caddy**（自动 ACME，运维最轻）。已起草 `infra/caddy/Caddyfile`（边缘 TLS 终结 + HTTP→HTTPS + HSTS + 反代现有 frontend nginx）与 `docker-compose.prod.yml`（caddy 独占宿主 80/443、frontend 不再发布宿主端口、backend `SESSION_COOKIE_SECURE=true`），并新增 `.github/workflows/compose-config.yml` 校验 demo 与生产 compose 可解析。**本地/CI 仅验证 compose 结构可解析**；真实证书签发需公网域名与可达 ACME，须**部署时验证**。内网/离线可改用 `tls internal` 或挂载证书。

## 背景（Context）

✅ **现状**（[security-architecture.md §5](../../architecture/security-architecture.md)）：生产经 Nginx——浏览器 → `:80` → `/api/` 反代 → `backend:8080`，**全程 HTTP**；仓库内**未见 TLS 配置**（🔶 此前推断由外层基础设施终结，未确认）。OQ-OPS-3 一直把 TLS 列为松散"待确认"。

✅ **触发（2026-06-07）**：[ADR-0016](ADR-0016-server-side-session-auth.md) 选定服务端会话 + **`Secure` + `SameSite` cookie**。浏览器**只在 HTTPS 下回传 `Secure` cookie**，`SameSite=None` 亦强制要求 `Secure`。因此：
- **生产无 HTTPS → 会话 cookie 不工作 → 鉴权不可用**。TLS 由"加固项"变为**会话鉴权的硬依赖**。

✅ **其它强化理由**：系统传输**儿童 PII / RRN / CCTV 流凭证**，明文 HTTP 传输在合规上不可接受。

✅ **约束**：单人维护（偏好自动化证书、低运维）；产品**尚未上线**（greenfield，可一次配好）；存在前置反代 Nginx（`frontend/nginx.conf`）。

## 决策（Decision）

**生产强制 HTTPS，在边缘反向代理终结 TLS**：

1. **终结点 = 边缘反代**。推荐**自动证书反代**（Caddy / Traefik，内置 ACME/Let's Encrypt 自动签发与续期）置于最前，反代到现有 frontend Nginx + backend；或在现有 frontend Nginx 上启用 TLS（443 + 证书挂载）。二选一属实现，但终结点统一在边缘。
2. **HTTP→HTTPS 强制重定向** + **HSTS**（`Strict-Transport-Security`）。
3. **配合 [ADR-0016](ADR-0016-server-side-session-auth.md)**：生产 cookie 置 `Secure`；同源（同域名经反代 `/api`）下 `SameSite=Lax`。
4. **开发态**：localhost 可放宽（`Secure` 关闭 / `SameSite=Lax`），仅生产强制，避免本地开发摩擦。
5. **证书与密钥**：走与 JWT/DB 密钥同一的环境注入/密钥管理路径（关联 [ADR-0016](ADR-0016-server-side-session-auth.md) 凭据范式）。

## 后果（Consequences）

- **正面**：满足 ADR-0016 会话 cookie 的硬前置；儿童 PII/RRN 传输加密（合规）；HSTS 防降级；自动证书反代降低单人运维负担。
- **负面 / 代价**：
  - 新增边缘反代组件（或扩展现有 Nginx）+ 证书生命周期管理。
  - 开发/生产配置分叉（dev 放宽 Secure）。
  - 域名依赖：ACME 自动签发需公网可达域名；纯内网/演示需自签或预置证书。
- **影响范围**：`frontend/nginx.conf` 或新增反代、`docker-compose.yml`、`operations/{deployment,configuration}.md`、[ADR-0016](ADR-0016-server-side-session-auth.md) 的 cookie 属性、OQ-OPS-3。

## 考虑过的备选（Alternatives Considered）

- **维持 HTTP，靠外层基础设施"可能"终结 TLS（现状推断）** — 否决：与 ADR-0016 的 `Secure` cookie 直接冲突（无 HTTPS 则鉴权不工作）；儿童 PII 明文传输不可接受；"是否有外层 TLS"长期含糊。
- **在 backend（Spring）直接终结 TLS** — 未采用：把证书/TLS 逻辑塞进应用层，与既有 Nginx 边缘模式重复，运维更重；边缘终结是惯例。
- **仅生产手工配置、不立决策** — 否决：TLS 现在是 ADR-0016 的硬依赖，需明确终结点与强制策略，否则会话鉴权上线即失效。

## 关联（References）

- 被 [ADR-0016](ADR-0016-server-side-session-auth.md) 触发（`Secure` cookie 硬依赖）；与 [ADR-0012](ADR-0012-production-data-lifecycle.md)（生产部署）耦合。
- 决断 [OQ-OPS-3](../../modernization/open-questions.md)（TLS/HTTPS 终结位置）。
- [security-architecture.md §5](../../architecture/security-architecture.md)、[operations/deployment.md](../../operations/deployment.md)。
- [roadmap.md](../../modernization/roadmap.md)。
