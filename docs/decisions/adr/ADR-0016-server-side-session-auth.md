---
ADR: ADR-0016
title: "ADR-0016: 服务端会话鉴权（Server-Side Session，替代无状态 JWT）"
status: Accepted
date: 2026-06-07
deciders: 接手人起草，维护者 Accept（2026-06-07）；取代 ADR-0007；实现委派独立 session
---

# ADR-0016: 服务端会话鉴权（Server-Side Session，替代无状态 JWT）

> **前瞻提案**。维护者 2026-06-07 Accept。**取代 [ADR-0007](ADR-0007-jwt-stateless-auth.md)（无状态 JWT）的会话机制方向。**

## 状态（Status）

Accepted（2026-06-07）。**Supersedes [ADR-0007](ADR-0007-jwt-stateless-auth.md)**。落地次序：**先于 [ADR-0009](ADR-0009-restore-auth-enforcement.md)（鉴权恢复）**——会话机制定了，0009 才按 session 落地，避免先恢复 JWT 再返工。**实现委派独立 Implementation session。**

## 背景（Context）

✅ **现状**：[ADR-0007](ADR-0007-jwt-stateless-auth.md) 选用无状态 JWT；当前鉴权**未启用**（`SecurityConfig` 全 `permitAll()` + JWT 过滤器注释，OQ-SEC-1）。JWT 栈已实现但**未生效**。

✅ **业务约束（维护者 2026-06-07 确认）**：
- 客户端**永远是浏览器**——前端为响应式 Web（含手机浏览器），**不上原生 App**（`device_tokens` 仅存 **Pushover 第三方推送 token**，非原生 App 凭据）。故 bearer-token 的"移动端便利"在此**不适用**；cookie 在桌面/手机浏览器行为一致。
- 敏感**儿童 PII / RRN**、多租户、**需即时吊销权限**（解雇教师 / 离职监护人须立即断访问）。
- **单人维护**，偏好简单。
- **产品尚未上线**——无存量会话/用户，切换鉴权机制为 **greenfield、零迁移成本**。
- **Redis 已有 compose**（`db/redis-docker-compose.yml`，OQ-OPS-2）；早期曾拟用于 session 后弃用。

✅ **纯无状态 JWT 的硬伤**：token 过期前**无法吊销**（对儿童安全应用不可接受）；若存 `localStorage` 则 **XSS 暴露**；前端需维护 token 存储 + refresh + 401 重放。

## 决策（Decision）

采用**服务端会话鉴权**：**Spring Session + Redis** 存会话，会话 id 经 **`httpOnly` + `Secure` + `SameSite` cookie** 投递；登录建会话、登出/吊销即删 Redis 会话。

1. **即时吊销**：删 Redis 会话即下线；可吊销单用户或全体（角色/权限变更即时生效）。
2. **抗 XSS**：会话 id 在 `httpOnly` cookie，JS 不可读。
3. **CSRF 防护**：生产同源（Nginx 托管前端 + 反代 `/api`）下 `SameSite=Lax` 为主，写操作叠加 Spring Security 内置 **CSRF token**（cookie 下发 + 前端回显请求头）。
4. **前端简化**：移除 `apiClient.ts` 的 token 存储 / refresh / 401 重放；改为 `withCredentials: true` + CSRF 头。
5. **复用既有 Redis**（OQ-OPS-2 弃案复活），并入主 `docker-compose.yml`。

## 后果（Consequences）

- **正面**：即时吊销（安全刚需）；XSS 防护（保护儿童 PII）；前端**净减代码**；手机网页与桌面无差异；复用既有 Redis；角色/权限变更即时生效。
- **负面 / 代价**：
  - 引入 **CSRF 防护**（Spring Security 内置，低成本）。
  - 后端**有状态**：每请求一次 Redis 会话查（亚毫秒，本规模无感）；依赖 Redis 可用性。
  - 一次性迁移——但因**未上线 + JWT 未启用**，成本极低、无过渡方案。
  - 开发态跨源（`localhost:3000 → :8080`）需 cookie 配置：`withCredentials` + CORS `allowCredentials`（`SecurityConfig` 已 `setAllowCredentials(true)`）+ `SameSite`/`Secure` 处理。
- **影响范围**：`backend`（`spring-session-data-redis`、`SecurityConfig`、登录/登出、弃用 `JwtUtil`/`JwtAuthenticationFilter`）、`frontend`（`apiClient.ts` 去 token 化）、`docker-compose.yml`（Redis 入主栈）、[ADR-0007](ADR-0007-jwt-stateless-auth.md)（Superseded）、[ADR-0009](ADR-0009-restore-auth-enforcement.md)（机制重塑）、文档（security-architecture / operations）。

## 考虑过的备选（Alternatives Considered）

- **无状态 JWT（[ADR-0007](ADR-0007-jwt-stateless-auth.md) 原方向）** — 本 ADR 取代：不可即时吊销、`localStorage` 存储 XSS 暴露、前端复杂；其"无状态/移动端"优势在**客户端恒为浏览器**前提下不适用。
- **JWT-done-right（短 access + Redis 可吊销 refresh + httpOnly cookie）** — 未采用：比直接 cookie session 更复杂，且仍有 access 寿命内的**延迟吊销窗口**；无原生客户端需要 bearer，收益不抵复杂度。
- **不透明 bearer token + Redis 校验（A′）** — 未采用：bearer 投递只为原生 App 服务，本项目无此客户端，反而牺牲 `httpOnly` 的 XSS 防护。

## 关联（References）

- **Supersedes [ADR-0007](ADR-0007-jwt-stateless-auth.md)**；**重塑 [ADR-0009](ADR-0009-restore-auth-enforcement.md)**（鉴权恢复的机制由 JWT 改为 session，ADR-0016 排其前）。
- 复活 Redis 用途（[OQ-OPS-2](../../modernization/open-questions.md)）；决断 [OQ-SEC-9](../../modernization/open-questions.md)。
- 依赖 [ADR-0014](ADR-0014-test-baseline.md)（落地补 characterization 测试）。
- 代码：`SecurityConfig.java`、`AuthService.java`、`JwtUtil.java` / `JwtAuthenticationFilter.java`（将弃用）、`frontend/src/services/.../apiClient.ts`、`db/redis-docker-compose.yml`。
- [roadmap.md](../../modernization/roadmap.md)。
