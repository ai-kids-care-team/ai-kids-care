---
ADR: ADR-0007
title: "ADR-0007: JWT 无状态鉴权"
status: Accepted (Retrospective)
date: 2026-05-29
deciders: 原始团队（逆向补记）
---

# ADR-0007: JWT 无状态鉴权

> **回溯性 ADR**：描述代码现状，非新提案。⚠️ 该机制当前在后端**处于停用状态**。

## 状态

Accepted (Retrospective)，⚠️ 当前停用（见后果）

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
- ❓ 鉴权关闭是临时（开发/演示）还是疏漏，需团队确认（OQ-SEC-1）。

## 考虑过的备选

- ❓ 服务端会话（Spring Session）——未采用，选择无状态 JWT。

## 关联

- [architecture/security-architecture.md](../../architecture/security-architecture.md)
- [modernization/open-questions.md](../../modernization/open-questions.md)
