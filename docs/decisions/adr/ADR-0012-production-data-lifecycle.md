---
ADR: ADR-0012
title: "ADR-0012: 区分演示重置与生产部署的数据生命周期"
status: Accepted
date: 2026-05-29
deciders: 维护者（2026-05-29 Accept；schema 管理推荐 Flyway/Liquibase，最终选型留 Implementation）
---

# ADR-0012: 区分演示重置与生产部署的数据生命周期

> **前瞻提案**。方向已由维护者于 2026-05-29 确认（OQ-OPS-1）：当前删卷为**演示重置**（符合预期）；**生产端将一并去除「删卷」与「插入 seed」流程**。本 ADR 形式化两套数据生命周期，并提出生产端 schema 演进机制。

## 状态（Status）

Accepted（2026-05-29 签署；schema 管理机制推荐 Flyway/Liquibase 作为方向，最终选型留待 Implementation 子 ADR）

## 背景（Context）

✅ CI（`Jenkinsfile:20`）执行 `docker compose down --remove-orphans --volumes --rmi local` → `up -d --build`：每次部署**清空** `postgres_data`/`neo4j_data` 卷。
✅ 数据库初始化依赖 `db/initdb/*.sql`：建表（`01`）+ 字典（`02`/`03`）+ 大量种子（`21..46`、`88`）+ 序列同步（`99`）。PostgreSQL **仅在数据目录为空时**执行 initdb。
✅ Neo4j 由 data-loader 一次性从 PG 加载（`restart:"no"`），无增量同步（OQ-DATA-1）。
> 事实后果：在保留数据的环境运行当前 CI 将**丢失全部数据**（current-state-assessment 列为高风险）。

## 决策（Decision）

将**演示/CI 重置**与**生产部署**拆分为两条独立的数据生命周期：

- **演示/CI（保留现状）**：可继续 `down --volumes` + initdb 种子，用于每次干净重建。
- **生产部署（新增）**：
  1. **不**执行 `down --volumes`（卷持久化）；
  2. **不**执行种子脚本（`21..46`、`88` 视为开发夹具，仅限 dev/demo）；
  3. 采用**前向式 schema 迁移**机制管理结构演进（不丢数据）。

生产 schema 管理（待选）：
- **A) 引入 Flyway/Liquibase**：以现有 `01_create_schema.sql` 为基线（baseline），后续以版本化迁移演进。**（推荐）**
- **B) 保留 initdb + 环境开关跳过 seed + 永不删卷**：改动最小，但 initdb 只在空目录运行，**无法**承担结构演进，长期不足。

## 后果（Consequences）

- **正面**：消除"CI 删卷导致生产数据丢失"的高风险；获得安全的结构演进路径；种子退化为开发夹具。
- **负面 / 代价**：
  - 需建立迁移基线与流水线分离（dev/demo vs prod）。
  - 需配套**备份/恢复与回滚**策略（OQ-OPS-4 当前缺失）。
  - Neo4j 派生视图的生产**再同步/增量**策略需一并设计（关联 OQ-DATA-1，可另立 ADR）。
- **影响范围**：`Jenkinsfile`/CI、`db/initdb/`、`docker-compose.yml`、`operations/` 文档。

## 考虑过的备选（Alternatives Considered）

- **生产沿用当前 CI（删卷 + 种子）** — 否决（维护者）：等同每次清库，生产不可接受。
- **手工 SQL 改库** — 否决：不可重复、不可审计、易出错。

## 关联（References）

- [operations/deployment.md](../../operations/deployment.md)、[operations/runbook.md](../../operations/runbook.md)、[data-architecture.md §3](../../architecture/data-architecture.md)、[open-questions.md](../../modernization/open-questions.md)（OQ-OPS-1，关联 OQ-OPS-4 / OQ-DATA-1）。
- 代码：`Jenkinsfile:17-24`、`db/initdb/*.sql`、`docker-compose.yml`。
