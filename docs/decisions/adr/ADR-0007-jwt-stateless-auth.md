---
ADR: ADR-0007
title: "ADR-0007: JWT 无状态鉴权"
status: "Superseded by ADR-0016 (2026-06-07; was Accepted Retrospective)"
date: 2026-05-29
deciders: 原始团队（逆向补记）；2026-06-07 被 ADR-0016 取代
---

# ADR-0007: JWT 无状态鉴权

> **回溯性 ADR**：描述代码现状，非新提案。⚠️ 该机制当前停用；并已被 **[ADR-0016](ADR-0016-server-side-session-auth.md)（服务端会话）取代**——会话方向改为服务端 session。本 ADR 仅作"当初选了 JWT"的历史记录保留，**决策正文不改**。

## 状态

**Superseded by [ADR-0016](ADR-0016-server-side-session-auth.md)**（2026-06-07）。原为 Accepted (Retrospective)、当前停用——会话机制改为服务端 session（见 ADR-0016）。

## 背景

✅ 后端具备完整的无状态 JWT 鉴权部件：`JwtUtil`（HMAC-SHA 签名）、`JwtAuthenticationFilter`、`BCryptPasswordEncoder`，`SessionCreationPolicy.STATELESS`。前端 `apiClient.ts` 实现了 Bearer 注入与 401→refresh→重放→强制登录的完整流程。

## 决策

采用 **JWT 无状态鉴权**：登录签发 access/refresh 令牌，前端在 `Authorization: Bearer` 中携带，服务端无会话。

## 后果

- **正面**：无状态、易水平扩展，契合静态前端 + REST 后端。
- ⚠️ **当前实际态势（已确认）**：`SecurityConfig` 中 `/api/v1/**` 为 `permitAll()` 且 `JwtAuthenticationFilter` 的注册被**注释**——**鉴权未被强制**，全部 API 对无凭证请求开放。
- **已确认的设计缺口**：
  - access 与 refresh 令牌由同一方法生成、无类型区分（refresh 不提供额外安全边界）。
  - 令牌不含角色 claim；JWT secret 有硬编码默认值；`expireSecond` 字段名与毫秒单位不符。
- ✅ **已确认（OQ-SEC-1）**：鉴权关闭为**临时演示态**（非疏漏），计划第一轮重构后恢复 → [ADR-0009](ADR-0009-restore-auth-enforcement.md)（Accepted）。

## 考虑过的备选

- 📌 **服务端会话（Spring Session，曾拟用 Redis）——曾考虑，后改为无状态 JWT**（维护者 2026-06-07 口述）：项目早期**考虑过用 Redis 做服务端 session**，后改成 JWT；**改用原因未记录、现维护者不掌握**（决策由原团队成员做出，非脑补）。仓库残留的 `db/redis-docker-compose.yml`（OQ-OPS-2）很可能即此弃案遗留。⏳ JWT vs 服务端 session 的**前瞻取舍正在评估**（见 [open-questions OQ-SEC-9](../../modernization/open-questions.md)）。

## 关联

- [architecture/security-architecture.md](../../architecture/security-architecture.md)
- [modernization/open-questions.md](../../modernization/open-questions.md)
