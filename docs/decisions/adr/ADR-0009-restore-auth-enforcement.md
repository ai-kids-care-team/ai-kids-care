---
ADR: ADR-0009
title: "ADR-0009: 恢复后端鉴权强制"
status: Accepted
date: 2026-05-29
deciders: 维护者（2026-05-29 Accept；落地时机：第一轮重构完成后）
---

# ADR-0009: 恢复后端鉴权强制

> **前瞻提案**（非回溯）。方向已由维护者于 2026-05-29 确认（OQ-SEC-1）：当前 `permitAll` 为临时演示态，计划在**第一轮重构完成后**恢复鉴权。本 ADR 形式化该决策并界定"恢复时必须一并修正"的范围。

## 状态（Status）

Accepted（2026-05-29 签署；落地时机：第一轮重构完成后）

## 背景（Context）

✅ [ADR-0007](ADR-0007-jwt-stateless-auth.md) 已记录后端具备完整的 JWT 无状态鉴权部件，但当前**未强制**：
- `SecurityConfig.java:48` `/api/v1/**` 为 `permitAll()`；`:51` `addFilterBefore(jwtAuthFilter, ...)` 被注释。
- 后果：全部业务 API 对无凭证请求开放，角色/多租户模型未被运行时强制（current-state-assessment 列为最高风险之一）。

✅ 恢复鉴权时绕不开的既有缺口（均已确认）：
- access 与 refresh 由同一 `generateToken` 生成、无类型区分（`AuthService.java:125-126`，OQ-SEC-2）。
- JWT secret 含硬编码默认值（`application.yml:27`，OQ-SEC-3）；`expireSecond` 字段名与毫秒单位不符（`JwtUtil.java:30`）。
- 令牌不含角色/权限 claim（`JwtUtil.generateToken` 仅置 subject）——无 claim 则授权（authorization）无依据。
- 🔶 前端已就绪：`apiClient.ts:38-85` 完整实现 401→refresh→重放→强制登录，恢复后无需前端改造。

🔶 运行时多租户过滤（OQ-SEC-8）与鉴权强相关，但范围更大，建议单独决策（见"关联"）。

## 决策（Decision）

恢复 **JWT 无状态鉴权的运行时强制**，并将其作为一个**最小自洽的安全基线**整体交付，而非仅取消注释：

1. 启用 `JwtAuthenticationFilter`；将 `/api/v1/**` 由 `permitAll()` 改为 `authenticated()`。
2. 明确**公开端点白名单**：`/auth/login`、`/auth/refresh`、`/auth/register`、`/auth/register/availability`、`/swagger-ui/**`、`/v3/api-docs/**`、`OPTIONS /**`。
3. 修正令牌正确性（否则强制后安全边界名存实亡）：access/refresh 类型与有效期区分（OQ-SEC-2）；JWT secret 生产强制外部注入、移除默认回退（OQ-SEC-3）；在令牌中加入角色/权限 claim 以支撑授权。

## 后果（Consequences）

- **正面**：角色模型与租户隔离获得运行时强制；消除"全 API 裸奔"的最高风险；与静态前端 + 无状态后端形态一致。
- **负面 / 代价**：
  - 需为每个端点确认正确的鉴权/授权要求；白名单遗漏会导致登录不可用或越权。
  - 当前**无自动化测试**（OQ-TEST-1）——恢复鉴权应配套鉴权/授权集成测试，否则回归风险高。
  - 修正 access/refresh 与 claim 结构会改变 `TokenVO`/前端契约的部分语义，需同步校验。
- **影响范围**：`backend/security/`、`config/SecurityConfig`、`AuthService`/`JwtUtil`、API 契约文档、前端 401 流程（仅验证）。

## 考虑过的备选（Alternatives Considered）

- **维持 permitAll，仅靠网络隔离** — 否决：涉及儿童 PII，缺乏纵深防御；与已实现的前端鉴权逻辑矛盾。
- **在 Nginx/网关层做鉴权** — 未采用：会把鉴权逻辑移出后端、与既有 Spring Security 部件重复；可作为补充（TLS/网关）但非主路径。
- **改用服务端会话（Spring Session）** — 否决：[ADR-0007](ADR-0007-jwt-stateless-auth.md) 已选定无状态，且前端为静态导出。

## 关联（References）

- 取代 [ADR-0007](ADR-0007-jwt-stateless-auth.md) 的"⚠️ 当前停用"态势（本 ADR 落地后应将 0007 标注更新）。
- [security-architecture.md](../../architecture/security-architecture.md)、[open-questions.md](../../modernization/open-questions.md)（OQ-SEC-1/2/3，关联 OQ-SEC-8 租户强制宜另立 ADR）。
- 代码：`SecurityConfig.java:40-52`、`AuthService.java:118-171`、`JwtUtil.java`、`apiClient.ts`。
