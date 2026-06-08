---
ADR: ADR-0018
title: "ADR-0018: 通知子系统（后端拥有、复核门控）"
status: Accepted
date: 2026-06-07
deciders: 接手人起草，维护者 Accept（2026-06-07）；实现委派独立 session
---

# ADR-0018: 通知子系统（Notification Subsystem）

> **前瞻提案**。固化多轮对话中已确认的通知事实，并把"通知"作为一个**子系统决策**沉淀（[ADR-0015](ADR-0015-ai-detection-closed-loop.md) 只覆盖了触发点，未覆盖子系统本身）。

## 状态（Status）

Accepted（2026-06-07）。与 [ADR-0015](ADR-0015-ai-detection-closed-loop.md)（AI 闭环）配套，排在其**前/同期**落地（闭环的"通知"步依赖本子系统）。实现委派独立 session。

## 背景（Context）

✅ **已有部件**：`notifications` / `notification_rules` / `device_tokens` 表 + `NotificationController` / `NotificationRuleController` / `DeviceTokenController`。
✅ **维护者确认（2026-06-07）**：
- **通知由后端负责**——后端从 DB 查询收件人后发送；**推理端（AI）不发通知**。
- AI 中现存的 Pushover/SMS 是**收件人写死的临时演示代码**（不查 DB），**非最终方案**，闭环落地时移除/替换。
- **`device_tokens` = Pushover 设备 token**：Pushover 是第三方推送 App，注册用户各有一个 device token，系统存取后在需要时推送给某人或某批人。
- **时机**：面向**家长**的通知须在**人工复核（`event_reviews`）确认后**发出；园方/教职工可对高置信检测做即时预警。
✅ **现状问题**：`notifications` 表多个字段（`sent_at` / `fail_reason` / `retry_count`）为 `NOT NULL`（OQ-DATA-3），但新建通知时这些语义上可能尚无值。

## 决策（Decision）

通知作为**后端拥有的子系统**，统一承载触发、规则、收件人解析、投递与重试：

1. **所有权**：后端是通知的**唯一发起方**；AI 仅写检测入库（[ADR-0015](ADR-0015-ai-detection-closed-loop.md) V1），由后端 `LISTEN/NOTIFY` 到检测后驱动通知。**移除 AI 侧的 Pushover/SMS 演示代码**。
2. **触发门控**：
   - 面向**家长（GUARDIAN）**的通知 → **仅在 `event_reviews` 复核确认后**发出（杜绝误报直推家长）。
   - 面向**园方/教职工**的高置信检测 → 可即时预警（落地细化阈值）。
   - 非检测类通知（公告等）按各自规则。
3. **规则引擎**：以 `notification_rules` 驱动"谁、何渠道、何条件"；收件人由 `device_tokens` 解析。
4. **渠道**：以 **Pushover**（`device_tokens`）为首发已实现渠道；SMS 为既有能力；Email 列为后续可选（README 提及）。渠道经统一抽象，便于增减。
5. **可靠性**：投递失败记录 `fail_reason` + `retry_count`，定义重试策略；**放宽 `notifications` 过严的 `NOT NULL`**（OQ-DATA-3）——`sent_at`/`fail_reason`/`retry_count` 在"待发"态应允许空/默认值（schema 变更走 [ADR-0012](ADR-0012-production-data-lifecycle.md) 迁移）。
6. **幂等**：同一检测事件不重复通知（与 [ADR-0015](ADR-0015-ai-detection-closed-loop.md) 的 `dedup_key` 关联）。

## 后果（Consequences）

- **正面**：通知逻辑集中后端一处（不在 AI 重复）；家长侧避免误报告警疲劳；规则/渠道/重试有统一归属；闭环"通知"步可落地。
- **负面 / 代价**：
  - 依赖外部 Pushover/SMS 可用性；需处理投递失败与重试。
  - schema 变更（放宽 NOT NULL）须走迁移（[ADR-0012](ADR-0012-production-data-lifecycle.md)）。
  - 复核门控引入"检测→复核→通知"的人工延迟（这是有意的安全权衡）。
- **影响范围**：`backend`（Notification* 服务、规则引擎、LISTEN 触发）、`ai/`（**移除** Pushover/SMS 演示代码）、数据模型（`notifications` NOT NULL 放宽）、[ADR-0015](ADR-0015-ai-detection-closed-loop.md)（通知步）、文档（features / api / data-architecture）。

## 考虑过的备选（Alternatives Considered）

- **推理端（AI）直接发通知（现状演示）** — 否决：收件人写死、不查 DB、绕过规则引擎与复核门控；与"后端拥有通知"的设计意图相悖。
- **检测落库即直推家长（不经复核）** — 否决：儿童安全场景误报代价高，违反"家长通知须复核后"的产品决策。
- **引入独立通知中间件/队列** — 当前不采用：单人维护下过重；现有 `notification_rules` + Pushover 足够，规模化时再议（与 [ADR-0015](ADR-0015-ai-detection-closed-loop.md) 的 broker 演进同节奏）。

## 关联（References）

- 配套 [ADR-0015](ADR-0015-ai-detection-closed-loop.md)（通知触发点）；schema 变更经 [ADR-0012](ADR-0012-production-data-lifecycle.md)。
- 决断 [OQ-DATA-3](../../modernization/open-questions.md)（`notifications` NOT NULL 过严）。
- 代码：`notifications`/`notification_rules`/`device_tokens` 表、`Notification*`/`DeviceToken*` 后端、`ai/src/ai_app/utils/{pushover,sms}.py`（待移除）。
- [roadmap.md](../../modernization/roadmap.md)。
